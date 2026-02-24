package com.example.mediaworker.service.ops;

import com.example.mediaworker.dto.internal.ops.request.MediaWorkerBackfillRequest;
import com.example.mediaworker.dto.internal.ops.response.MediaWorkerBackfillResponse;
import com.example.mediaworker.dto.internal.ops.response.MediaWorkerTaskSummaryResponse;
import com.example.mediaworker.entity.MediaDerivativeProfile;
import com.example.mediaworker.entity.MediaDerivativeTask;
import com.example.mediaworker.entity.MediaDerivativeTaskStatus;
import com.example.mediaworker.entity.MediaFileRecord;
import com.example.mediaworker.repository.MediaDerivativeTaskDlqRepository;
import com.example.mediaworker.repository.MediaDerivativeTaskRepository;
import com.example.mediaworker.repository.MediaFileRecordRepository;
import com.example.mediaworker.service.MediaDerivativeTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaWorkerOpsServiceTest {

    @Mock
    private MediaDerivativeTaskRepository mediaDerivativeTaskRepository;

    @Mock
    private MediaDerivativeTaskDlqRepository mediaDerivativeTaskDlqRepository;

    @Mock
    private MediaFileRecordRepository mediaFileRecordRepository;

    @Mock
    private MediaDerivativeTaskService mediaDerivativeTaskService;

    @Test
    void getTaskSummary_aggregatesAllStatusCounts() {
        MediaWorkerOpsService service = createService();

        when(mediaDerivativeTaskRepository.countByStatus(MediaDerivativeTaskStatus.PENDING)).thenReturn(3L);
        when(mediaDerivativeTaskRepository.countByStatus(MediaDerivativeTaskStatus.PROCESSING)).thenReturn(2L);
        when(mediaDerivativeTaskRepository.countByStatus(MediaDerivativeTaskStatus.COMPLETED)).thenReturn(11L);
        when(mediaDerivativeTaskRepository.countByStatus(MediaDerivativeTaskStatus.FAILED)).thenReturn(1L);
        when(mediaDerivativeTaskDlqRepository.count()).thenReturn(4L);

        MediaWorkerTaskSummaryResponse summary = service.getTaskSummary();

        assertThat(summary.getPendingCount()).isEqualTo(3L);
        assertThat(summary.getProcessingCount()).isEqualTo(2L);
        assertThat(summary.getCompletedCount()).isEqualTo(11L);
        assertThat(summary.getFailedCount()).isEqualTo(1L);
        assertThat(summary.getDlqCount()).isEqualTo(4L);
    }

    @Test
    void replayFailedTask_requeuesFailedTask() {
        MediaWorkerOpsService service = createService();
        MediaDerivativeTask failedTask = MediaDerivativeTask.createPending(10L, MediaDerivativeProfile.THUMBNAIL_WEBP, 1L, "event-10");
        ReflectionTestUtils.setField(failedTask, "id", 100L);
        ReflectionTestUtils.setField(failedTask, "status", MediaDerivativeTaskStatus.FAILED);

        MediaDerivativeTask replayedTask = MediaDerivativeTask.createPending(10L, MediaDerivativeProfile.THUMBNAIL_WEBP, 1L, "event-10");
        ReflectionTestUtils.setField(replayedTask, "id", 100L);
        ReflectionTestUtils.setField(replayedTask, "status", MediaDerivativeTaskStatus.PENDING);
        ReflectionTestUtils.setField(replayedTask, "lastError", "manual replay requested");

        when(mediaDerivativeTaskRepository.findById(100L)).thenReturn(Optional.of(failedTask));
        when(mediaDerivativeTaskService.replayFailedTask(eq(100L), eq("manual replay requested"), any(LocalDateTime.class)))
                .thenReturn(Optional.of(replayedTask));

        MediaDerivativeTask result = service.replayFailedTask(100L, " ");

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getStatus()).isEqualTo(MediaDerivativeTaskStatus.PENDING);
    }

    @Test
    void replayFailedTask_rejectsNonFailedTask() {
        MediaWorkerOpsService service = createService();
        MediaDerivativeTask completedTask = MediaDerivativeTask.createPending(11L, MediaDerivativeProfile.THUMBNAIL_WEBP, 1L, "event-11");
        ReflectionTestUtils.setField(completedTask, "id", 101L);
        ReflectionTestUtils.setField(completedTask, "status", MediaDerivativeTaskStatus.COMPLETED);

        when(mediaDerivativeTaskRepository.findById(101L)).thenReturn(Optional.of(completedTask));

        assertThatThrownBy(() -> service.replayFailedTask(101L, "retry"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FAILED");
        verify(mediaDerivativeTaskService, never())
                .replayFailedTask(eq(101L), any(), any(LocalDateTime.class));
    }

    @Test
    void backfill_enqueuesOnlyMissingTasks() {
        MediaWorkerOpsService service = createService();
        MediaWorkerBackfillRequest request = new MediaWorkerBackfillRequest();
        ReflectionTestUtils.setField(request, "size", 10);
        ReflectionTestUtils.setField(request, "derivativeProfile", "THUMBNAIL_WEBP");

        MediaFileRecord candidateA = mock(MediaFileRecord.class);
        MediaFileRecord candidateB = mock(MediaFileRecord.class);
        when(candidateA.getId()).thenReturn(201L);
        when(candidateB.getId()).thenReturn(202L);

        when(mediaFileRecordRepository.findBackfillCandidates(any(), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(List.of(candidateA, candidateB));
        when(mediaDerivativeTaskRepository.findByMediaIdAndDerivativeProfileAndMediaVersion(
                201L, MediaDerivativeProfile.THUMBNAIL_WEBP, 1L
        )).thenReturn(Optional.empty());
        when(mediaDerivativeTaskRepository.findByMediaIdAndDerivativeProfileAndMediaVersion(
                202L, MediaDerivativeProfile.THUMBNAIL_WEBP, 1L
        )).thenReturn(Optional.of(MediaDerivativeTask.createPending(202L, MediaDerivativeProfile.THUMBNAIL_WEBP, 1L, "event")));

        MediaWorkerBackfillResponse response = service.backfill(request);

        assertThat(response.getScannedCount()).isEqualTo(2L);
        assertThat(response.getQueuedCount()).isEqualTo(1L);
        assertThat(response.getExistingCount()).isEqualTo(1L);
        verify(mediaDerivativeTaskService).enqueuePending(201L, 1L, MediaDerivativeProfile.THUMBNAIL_WEBP, "ops-backfill");
    }

    private MediaWorkerOpsService createService() {
        return new MediaWorkerOpsService(
                mediaDerivativeTaskRepository,
                mediaDerivativeTaskDlqRepository,
                mediaFileRecordRepository,
                mediaDerivativeTaskService
        );
    }
}
