package com.example.mediaworker.service;

import com.example.mediaworker.entity.MediaDerivativeProfile;
import com.example.mediaworker.entity.MediaDerivativeTask;
import com.example.mediaworker.entity.MediaDerivativeTaskStatus;
import com.example.mediaworker.service.ops.MediaWorkerMetricsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaDerivativeTaskSchedulerTest {

    @Mock
    private MediaDerivativeTaskService mediaDerivativeTaskService;

    @Mock
    private MediaDerivativeProcessor mediaDerivativeProcessor;

    @Mock
    private MediaWorkerMetricsService mediaWorkerMetricsService;

    @Test
    void dispatchDueTasks_onSuccess_marksCompleted() {
        MediaDerivativeTaskScheduler scheduler = new MediaDerivativeTaskScheduler(
                mediaDerivativeTaskService,
                mediaDerivativeProcessor,
                mediaWorkerMetricsService
        );
        MediaDerivativeTask processingTask = MediaDerivativeTask.createPending(1L, MediaDerivativeProfile.THUMBNAIL_WEBP, 1L, "event-1");
        ReflectionTestUtils.setField(processingTask, "id", 101L);
        ReflectionTestUtils.setField(processingTask, "status", MediaDerivativeTaskStatus.PROCESSING);

        when(mediaDerivativeTaskService.claimDueTaskIds(any(LocalDateTime.class))).thenReturn(List.of(101L));
        when(mediaDerivativeTaskService.findById(101L)).thenReturn(Optional.of(processingTask));

        scheduler.dispatchDueTasks();

        verify(mediaDerivativeProcessor).process(processingTask);
        verify(mediaDerivativeTaskService).markCompleted(eq(101L), any(LocalDateTime.class));
        verify(mediaDerivativeTaskService, never()).handleFailure(eq(101L), any(Exception.class), any(LocalDateTime.class));
        verify(mediaWorkerMetricsService).recordTaskSuccess(any());
        verify(mediaWorkerMetricsService, never()).recordTaskFailure(any());
    }

    @Test
    void dispatchDueTasks_onFailure_handlesRetryPath() {
        MediaDerivativeTaskScheduler scheduler = new MediaDerivativeTaskScheduler(
                mediaDerivativeTaskService,
                mediaDerivativeProcessor,
                mediaWorkerMetricsService
        );
        MediaDerivativeTask processingTask = MediaDerivativeTask.createPending(2L, MediaDerivativeProfile.THUMBNAIL_WEBP, 1L, "event-2");
        ReflectionTestUtils.setField(processingTask, "id", 102L);
        ReflectionTestUtils.setField(processingTask, "status", MediaDerivativeTaskStatus.PROCESSING);

        when(mediaDerivativeTaskService.claimDueTaskIds(any(LocalDateTime.class))).thenReturn(List.of(102L));
        when(mediaDerivativeTaskService.findById(102L)).thenReturn(Optional.of(processingTask));
        doThrow(new RuntimeException("processing failed")).when(mediaDerivativeProcessor).process(processingTask);
        when(mediaDerivativeTaskService.handleFailure(eq(102L), any(Exception.class), any(LocalDateTime.class)))
                .thenReturn(MediaDerivativeFailureHandleResult.retryScheduled(
                        MediaDerivativeFailureCode.RETRIABLE_EXCEPTION,
                        1
                ));

        scheduler.dispatchDueTasks();

        verify(mediaDerivativeTaskService).handleFailure(eq(102L), any(Exception.class), any(LocalDateTime.class));
        verify(mediaDerivativeTaskService, never()).markCompleted(eq(102L), any(LocalDateTime.class));
        verify(mediaWorkerMetricsService).recordTaskFailure(any());
        verify(mediaWorkerMetricsService).recordRetryScheduled();
        verify(mediaWorkerMetricsService, never()).recordDlq();
    }

    @Test
    void recoverStaleProcessingTasks_invokesRecoveryService() {
        MediaDerivativeTaskScheduler scheduler = new MediaDerivativeTaskScheduler(
                mediaDerivativeTaskService,
                mediaDerivativeProcessor,
                mediaWorkerMetricsService
        );
        when(mediaDerivativeTaskService.recoverStaleProcessingTasks(any(LocalDateTime.class))).thenReturn(1);

        scheduler.recoverStaleProcessingTasks();

        verify(mediaDerivativeTaskService).recoverStaleProcessingTasks(any(LocalDateTime.class));
        verify(mediaWorkerMetricsService).recordStaleRecovered(1);
    }
}
