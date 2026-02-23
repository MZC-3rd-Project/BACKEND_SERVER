package com.example.mediaworker.service;

import com.example.mediaworker.entity.MediaDerivativeTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaDerivativeTaskScheduler {

    private final MediaDerivativeTaskService mediaDerivativeTaskService;
    private final MediaDerivativeProcessor mediaDerivativeProcessor;

    @Scheduled(fixedDelayString = "${media.worker.tasks.dispatch-fixed-delay-ms:3000}")
    public void dispatchDueTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> claimedTaskIds = mediaDerivativeTaskService.claimDueTaskIds(now);
        if (claimedTaskIds.isEmpty()) {
            return;
        }

        for (Long taskId : claimedTaskIds) {
            processClaimedTask(taskId);
        }
    }

    @Scheduled(fixedDelayString = "${media.worker.tasks.stale-recovery-fixed-delay-ms:30000}")
    public void recoverStaleProcessingTasks() {
        int recoveredCount = mediaDerivativeTaskService.recoverStaleProcessingTasks(LocalDateTime.now());
        if (recoveredCount > 0) {
            log.warn("[MediaWorker] recovered stale processing tasks. count={}", recoveredCount);
        }
    }

    private void processClaimedTask(Long taskId) {
        Optional<MediaDerivativeTask> optionalTask = mediaDerivativeTaskService.findById(taskId);
        if (optionalTask.isEmpty()) {
            return;
        }
        MediaDerivativeTask task = optionalTask.get();
        try {
            mediaDerivativeProcessor.process(task);
            mediaDerivativeTaskService.markCompleted(taskId, LocalDateTime.now());
        } catch (Exception exception) {
            log.warn(
                    "[MediaWorker] derivative processing failed. taskId={}, mediaId={}, profile={}, reason={}",
                    task.getId(),
                    task.getMediaId(),
                    task.getDerivativeProfile(),
                    exception.getMessage()
            );
            mediaDerivativeTaskService.handleFailure(taskId, exception, LocalDateTime.now());
        }
    }
}
