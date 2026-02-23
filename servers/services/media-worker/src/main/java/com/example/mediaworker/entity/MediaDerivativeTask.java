package com.example.mediaworker.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "media_derivative_tasks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_media_derivative_tasks_media_profile_version",
                        columnNames = {"media_id", "derivative_profile", "media_version"}
                )
        },
        indexes = {
                @Index(name = "idx_media_derivative_tasks_status_next_retry", columnList = "status,next_retry_at"),
                @Index(name = "idx_media_derivative_tasks_status_updated", columnList = "status,updated_at")
        }
)
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaDerivativeTask extends BaseEntity {

    private static final int LAST_ERROR_MAX_LENGTH = 500;

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "media_id", nullable = false)
    private Long mediaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "derivative_profile", nullable = false, length = 60)
    private MediaDerivativeProfile derivativeProfile;

    @Column(name = "media_version", nullable = false)
    private Long mediaVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MediaDerivativeTaskStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "source_event_id", length = 120)
    private String sourceEventId;

    @Column(name = "last_error", length = LAST_ERROR_MAX_LENGTH)
    private String lastError;

    public static MediaDerivativeTask createPending(Long mediaId,
                                                    MediaDerivativeProfile derivativeProfile,
                                                    Long mediaVersion,
                                                    String sourceEventId) {
        MediaDerivativeTask task = new MediaDerivativeTask();
        task.mediaId = mediaId;
        task.derivativeProfile = derivativeProfile;
        task.mediaVersion = mediaVersion;
        task.status = MediaDerivativeTaskStatus.PENDING;
        task.retryCount = 0;
        task.sourceEventId = sourceEventId;
        task.nextRetryAt = null;
        task.processingStartedAt = null;
        task.completedAt = null;
        task.lastError = null;
        return task;
    }

    public boolean isTerminal() {
        return status == MediaDerivativeTaskStatus.COMPLETED || status == MediaDerivativeTaskStatus.FAILED;
    }

    public void markCompleted(LocalDateTime completedAt) {
        assertStatus(MediaDerivativeTaskStatus.PROCESSING);
        this.status = MediaDerivativeTaskStatus.COMPLETED;
        this.completedAt = completedAt;
        this.processingStartedAt = null;
        this.nextRetryAt = null;
        this.lastError = null;
    }

    public void scheduleRetry(LocalDateTime nextRetryAt, String errorMessage) {
        assertStatus(MediaDerivativeTaskStatus.PROCESSING);
        this.status = MediaDerivativeTaskStatus.PENDING;
        this.retryCount = this.retryCount == null ? 1 : this.retryCount + 1;
        this.processingStartedAt = null;
        this.nextRetryAt = nextRetryAt;
        this.lastError = truncate(errorMessage);
    }

    public void markFailed(String errorMessage, LocalDateTime failedAt) {
        assertStatus(MediaDerivativeTaskStatus.PROCESSING);
        this.status = MediaDerivativeTaskStatus.FAILED;
        this.retryCount = this.retryCount == null ? 1 : this.retryCount + 1;
        this.processingStartedAt = null;
        this.nextRetryAt = null;
        this.completedAt = failedAt;
        this.lastError = truncate(errorMessage);
    }

    public void recoverFromStaleProcessing(LocalDateTime retryAt, String errorMessage) {
        assertStatus(MediaDerivativeTaskStatus.PROCESSING);
        this.status = MediaDerivativeTaskStatus.PENDING;
        this.processingStartedAt = null;
        this.nextRetryAt = retryAt;
        this.lastError = truncate(errorMessage);
    }

    private void assertStatus(MediaDerivativeTaskStatus expected) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "Invalid task state transition. expected=" + expected + ", actual=" + this.status
            );
        }
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= LAST_ERROR_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
