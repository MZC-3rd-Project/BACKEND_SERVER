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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(
        name = "media_derivative_task_dlq",
        indexes = {
                @Index(name = "idx_media_derivative_task_dlq_task_id", columnList = "task_id"),
                @Index(name = "idx_media_derivative_task_dlq_media_id", columnList = "media_id")
        }
)
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaDerivativeTaskDlq extends BaseEntity {

    private static final int MAX_LENGTH = 500;

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "media_id", nullable = false)
    private Long mediaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "derivative_profile", nullable = false, length = 60)
    private MediaDerivativeProfile derivativeProfile;

    @Column(name = "media_version", nullable = false)
    private Long mediaVersion;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "error_code", nullable = false, length = 60)
    private String errorCode;

    @Column(name = "error_message", length = MAX_LENGTH)
    private String errorMessage;

    @Column(name = "source_event_id", length = 120)
    private String sourceEventId;

    public static MediaDerivativeTaskDlq fromTask(MediaDerivativeTask task, String errorCode, String errorMessage) {
        MediaDerivativeTaskDlq dlq = new MediaDerivativeTaskDlq();
        dlq.taskId = task.getId();
        dlq.mediaId = task.getMediaId();
        dlq.derivativeProfile = task.getDerivativeProfile();
        dlq.mediaVersion = task.getMediaVersion();
        dlq.retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        dlq.errorCode = truncate(errorCode);
        dlq.errorMessage = truncate(errorMessage);
        dlq.sourceEventId = task.getSourceEventId();
        return dlq;
    }

    private static String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= MAX_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_LENGTH);
    }
}
