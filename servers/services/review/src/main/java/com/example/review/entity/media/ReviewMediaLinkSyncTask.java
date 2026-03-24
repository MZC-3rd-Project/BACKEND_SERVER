package com.example.review.entity.media;

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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Entity
@Table(name = "review_media_link_sync_tasks", indexes = {
        @Index(name = "idx_review_media_link_sync_tasks_status_next", columnList = "status,next_retry_at"),
        @Index(name = "idx_review_media_link_sync_tasks_status_updated", columnList = "status,updated_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_review_media_link_sync_tasks_review", columnNames = {"review_id"})
})
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewMediaLinkSyncTask extends BaseEntity {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Column(name = "gallery_media_ids", nullable = false, length = 5000)
    private String galleryMediaIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReviewMediaLinkSyncStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    public static ReviewMediaLinkSyncTask create(Long reviewId, List<Long> galleryMediaIds, String errorMessage) {
        ReviewMediaLinkSyncTask task = new ReviewMediaLinkSyncTask();
        task.reviewId = reviewId;
        task.upsertPending(galleryMediaIds, errorMessage);
        return task;
    }

    public void upsertPending(List<Long> galleryMediaIds, String errorMessage) {
        this.galleryMediaIds = serializeGalleryMediaIds(galleryMediaIds);
        this.status = ReviewMediaLinkSyncStatus.PENDING;
        this.retryCount = 0;
        this.nextRetryAt = LocalDateTime.now();
        this.lastError = normalizeError(errorMessage);
    }

    public void markProcessing() {
        this.status = ReviewMediaLinkSyncStatus.PROCESSING;
    }

    public void markCompleted(List<Long> galleryMediaIds) {
        this.galleryMediaIds = serializeGalleryMediaIds(galleryMediaIds);
        this.status = ReviewMediaLinkSyncStatus.COMPLETED;
        this.retryCount = 0;
        this.nextRetryAt = LocalDateTime.now();
        this.lastError = null;
    }

    public void scheduleNextRetry(String errorMessage, long delaySeconds) {
        this.retryCount += 1;
        this.status = ReviewMediaLinkSyncStatus.PENDING;
        this.nextRetryAt = LocalDateTime.now().plusSeconds(Math.max(0, delaySeconds));
        this.lastError = normalizeError(errorMessage);
    }

    public void markFailed(String errorMessage) {
        this.retryCount += 1;
        this.status = ReviewMediaLinkSyncStatus.FAILED;
        this.lastError = normalizeError(errorMessage);
    }

    public boolean isProcessing() {
        return this.status == ReviewMediaLinkSyncStatus.PROCESSING;
    }

    public List<Long> getGalleryMediaIdList() {
        if (galleryMediaIds == null || galleryMediaIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(galleryMediaIds.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(ReviewMediaLinkSyncTask::parsePositiveLong)
                .filter(Objects::nonNull)
                .toList();
    }

    private static Long parsePositiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String serializeGalleryMediaIds(List<Long> galleryMediaIds) {
        if (galleryMediaIds == null || galleryMediaIds.isEmpty()) {
            return "";
        }
        return galleryMediaIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private static String normalizeError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "Review media link sync failed";
        }
        if (errorMessage.length() <= MAX_ERROR_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_LENGTH);
    }
}
