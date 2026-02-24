package com.example.mediaworker.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.util.ReflectionTestUtils.setField;

class MediaDerivativeTaskTest {

    @Test
    void markCompleted_requiresProcessingStatus() {
        MediaDerivativeTask task = MediaDerivativeTask.createPending(1L, MediaDerivativeProfile.THUMBNAIL_WEBP, 1L, "event-1");

        assertThatThrownBy(() -> task.markCompleted(LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);

        setField(task, "status", MediaDerivativeTaskStatus.PROCESSING);
        LocalDateTime completedAt = LocalDateTime.now();
        task.markCompleted(completedAt);

        assertThat(task.getStatus()).isEqualTo(MediaDerivativeTaskStatus.COMPLETED);
        assertThat(task.getCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    void scheduleRetry_movesToPendingAndIncrementsRetryCount() {
        MediaDerivativeTask task = MediaDerivativeTask.createPending(2L, MediaDerivativeProfile.THUMBNAIL_WEBP, 1L, "event-2");
        setField(task, "status", MediaDerivativeTaskStatus.PROCESSING);

        LocalDateTime retryAt = LocalDateTime.now().plusSeconds(30);
        task.scheduleRetry(retryAt, "temporary failure");

        assertThat(task.getStatus()).isEqualTo(MediaDerivativeTaskStatus.PENDING);
        assertThat(task.getRetryCount()).isEqualTo(1);
        assertThat(task.getNextRetryAt()).isEqualTo(retryAt);
    }

    @Test
    void markFailed_setsTerminalStatus() {
        MediaDerivativeTask task = MediaDerivativeTask.createPending(3L, MediaDerivativeProfile.THUMBNAIL_WEBP, 1L, "event-3");
        setField(task, "status", MediaDerivativeTaskStatus.PROCESSING);

        LocalDateTime failedAt = LocalDateTime.now();
        task.markFailed("non-retriable failure", failedAt);

        assertThat(task.getStatus()).isEqualTo(MediaDerivativeTaskStatus.FAILED);
        assertThat(task.getRetryCount()).isEqualTo(1);
        assertThat(task.getCompletedAt()).isEqualTo(failedAt);
        assertThat(task.isTerminal()).isTrue();
    }
}
