package com.example.mediaworker.service;

import com.example.mediaworker.config.MediaWorkerTaskProperties;
import com.example.mediaworker.entity.MediaDerivativeProfile;
import com.example.mediaworker.entity.MediaDerivativeTask;
import com.example.mediaworker.entity.MediaDerivativeTaskStatus;
import com.example.mediaworker.repository.MediaDerivativeTaskRepository;
import com.example.mediaworker.repository.MediaDerivativeTaskDlqRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaDerivativeTaskServiceTest {

    @Mock
    private MediaDerivativeTaskRepository mediaDerivativeTaskRepository;

    @Mock
    private MediaDerivativeTaskDlqRepository mediaDerivativeTaskDlqRepository;

    @Mock
    private MediaDerivativeFailureClassifier failureClassifier;

    private MediaWorkerTaskProperties properties;
    private MediaDerivativeTaskService mediaDerivativeTaskService;

    @BeforeEach
    void setUp() {
        properties = new MediaWorkerTaskProperties();
        properties.setBatchSize(10);
        properties.setMaxRetryCount(2);
        properties.setRetryInitialDelaySeconds(5);
        properties.setRetryMaxDelaySeconds(60);
        mediaDerivativeTaskService = new MediaDerivativeTaskService(
                mediaDerivativeTaskRepository,
                mediaDerivativeTaskDlqRepository,
                properties,
                failureClassifier
        );
    }

    @Test
    void enqueuePending_createsNewTaskWhenAbsent() {
        when(mediaDerivativeTaskRepository.findByMediaIdAndDerivativeProfileAndMediaVersion(1L, MediaDerivativeProfile.THUMBNAIL_WEBP, 1L))
                .thenReturn(Optional.empty());
        when(mediaDerivativeTaskRepository.save(any(MediaDerivativeTask.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MediaDerivativeTask task = mediaDerivativeTaskService.enqueuePending(
                1L,
                null,
                MediaDerivativeProfile.THUMBNAIL_WEBP,
                "event-1"
        );

        assertThat(task.getMediaId()).isEqualTo(1L);
        assertThat(task.getMediaVersion()).isEqualTo(1L);
        assertThat(task.getDerivativeProfile()).isEqualTo(MediaDerivativeProfile.THUMBNAIL_WEBP);
        assertThat(task.getStatus()).isEqualTo(MediaDerivativeTaskStatus.PENDING);
        assertThat(task.getRetryCount()).isEqualTo(0);
    }

    @Test
    void enqueuePending_whenConcurrentDuplicateInsert_returnsExistingTask() {
        MediaDerivativeTask existing = MediaDerivativeTask.createPending(2L, MediaDerivativeProfile.THUMBNAIL_WEBP, 1L, "event-a");
        ReflectionTestUtils.setField(existing, "id", 200L);

        when(mediaDerivativeTaskRepository.findByMediaIdAndDerivativeProfileAndMediaVersion(2L, MediaDerivativeProfile.THUMBNAIL_WEBP, 1L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(mediaDerivativeTaskRepository.save(any(MediaDerivativeTask.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        MediaDerivativeTask task = mediaDerivativeTaskService.enqueuePending(
                2L,
                1L,
                MediaDerivativeProfile.THUMBNAIL_WEBP,
                "event-b"
        );

        assertThat(task.getId()).isEqualTo(200L);
        assertThat(task.getMediaId()).isEqualTo(2L);
    }

    @Test
    void enqueuePending_rejectsInvalidMediaId() {
        assertThatThrownBy(() -> mediaDerivativeTaskService.enqueuePending(
                0L,
                1L,
                MediaDerivativeProfile.THUMBNAIL_WEBP,
                "event-invalid"
        )).isInstanceOf(IllegalArgumentException.class);

        verify(mediaDerivativeTaskRepository, never()).save(any(MediaDerivativeTask.class));
    }

    @Test
    void claimDueTaskIds_returnsOnlySuccessfullyClaimedTasks() {
        LocalDateTime now = LocalDateTime.now();
        MediaDerivativeTask due1 = MediaDerivativeTask.createPending(10L, MediaDerivativeProfile.THUMBNAIL_WEBP, 1L, "event-10");
        ReflectionTestUtils.setField(due1, "id", 101L);
        MediaDerivativeTask due2 = MediaDerivativeTask.createPending(11L, MediaDerivativeProfile.THUMBNAIL_WEBP, 1L, "event-11");
        ReflectionTestUtils.setField(due2, "id", 102L);

        when(mediaDerivativeTaskRepository.findDueTasks(eq(MediaDerivativeTaskStatus.PENDING), eq(now), any(Pageable.class)))
                .thenReturn(List.of(due1, due2));
        when(mediaDerivativeTaskRepository.claimDueTask(101L, MediaDerivativeTaskStatus.PENDING, MediaDerivativeTaskStatus.PROCESSING, now, now))
                .thenReturn(1);
        when(mediaDerivativeTaskRepository.claimDueTask(102L, MediaDerivativeTaskStatus.PENDING, MediaDerivativeTaskStatus.PROCESSING, now, now))
                .thenReturn(0);

        List<Long> claimedTaskIds = mediaDerivativeTaskService.claimDueTaskIds(now);

        assertThat(claimedTaskIds).containsExactly(101L);
    }

    @Test
    void handleFailure_schedulesRetryBeforeMaxCount() {
        LocalDateTime now = LocalDateTime.now();
        MediaDerivativeTask processing = MediaDerivativeTask.createPending(20L, MediaDerivativeProfile.THUMBNAIL_WEBP, 1L, "event-20");
        ReflectionTestUtils.setField(processing, "status", MediaDerivativeTaskStatus.PROCESSING);
        ReflectionTestUtils.setField(processing, "id", 201L);

        when(mediaDerivativeTaskRepository.findById(201L)).thenReturn(Optional.of(processing));
        when(failureClassifier.classify(any(), eq(1), eq(properties.getMaxRetryCount())))
                .thenReturn(new MediaDerivativeFailureDecision(true, MediaDerivativeFailureCode.RETRIABLE_EXCEPTION));

        mediaDerivativeTaskService.handleFailure(201L, new RuntimeException("temporary"), now);

        assertThat(processing.getStatus()).isEqualTo(MediaDerivativeTaskStatus.PENDING);
        assertThat(processing.getRetryCount()).isEqualTo(1);
        assertThat(processing.getNextRetryAt()).isAfter(now.minusSeconds(1));
        assertThat(processing.getLastError()).contains("RuntimeException");
        verify(mediaDerivativeTaskDlqRepository, never()).save(any());
    }

    @Test
    void handleFailure_marksFailedWhenRetryExceeded() {
        LocalDateTime now = LocalDateTime.now();
        properties.setMaxRetryCount(0);
        MediaDerivativeTask processing = MediaDerivativeTask.createPending(21L, MediaDerivativeProfile.THUMBNAIL_WEBP, 1L, "event-21");
        ReflectionTestUtils.setField(processing, "status", MediaDerivativeTaskStatus.PROCESSING);
        ReflectionTestUtils.setField(processing, "id", 202L);

        when(mediaDerivativeTaskRepository.findById(202L)).thenReturn(Optional.of(processing));
        when(failureClassifier.classify(any(), eq(1), eq(properties.getMaxRetryCount())))
                .thenReturn(new MediaDerivativeFailureDecision(false, MediaDerivativeFailureCode.MAX_RETRY_EXCEEDED));

        mediaDerivativeTaskService.handleFailure(202L, new RuntimeException("boom"), now);

        assertThat(processing.getStatus()).isEqualTo(MediaDerivativeTaskStatus.FAILED);
        assertThat(processing.getRetryCount()).isEqualTo(1);
        assertThat(processing.getCompletedAt()).isEqualTo(now);
        verify(mediaDerivativeTaskDlqRepository).save(any());
    }
}
