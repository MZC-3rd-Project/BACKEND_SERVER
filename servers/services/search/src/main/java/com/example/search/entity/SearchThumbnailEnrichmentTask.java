package com.example.search.entity;

import com.example.data.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "search_thumbnail_enrichment_tasks",
        indexes = {
                @Index(name = "idx_search_thumb_tasks_status_next", columnList = "status,next_retry_at"),
                @Index(name = "idx_search_thumb_tasks_status_updated", columnList = "status,updated_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_search_thumb_tasks_item", columnNames = {"item_id"})
        }
)
@SQLRestriction("deleted_at IS NULL")
public class SearchThumbnailEnrichmentTask extends BaseEntity {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "thumbnail_media_id", nullable = false)
    private Long thumbnailMediaId;

    @Column(name = "media_version", nullable = false)
    private Long mediaVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SearchThumbnailEnrichmentStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    public static SearchThumbnailEnrichmentTask create(Long itemId, Long thumbnailMediaId, Long mediaVersion) {
        SearchThumbnailEnrichmentTask task = new SearchThumbnailEnrichmentTask();
        task.itemId = itemId;
        task.upsertPending(thumbnailMediaId, mediaVersion);
        return task;
    }

    public void upsertPending(Long thumbnailMediaId, Long mediaVersion) {
        this.thumbnailMediaId = thumbnailMediaId;
        this.mediaVersion = mediaVersion;
        this.status = SearchThumbnailEnrichmentStatus.PENDING;
        this.retryCount = 0;
        this.nextRetryAt = LocalDateTime.now();
        this.lastError = null;
    }

    public void markCompleted() {
        this.status = SearchThumbnailEnrichmentStatus.COMPLETED;
        this.retryCount = 0;
        this.nextRetryAt = LocalDateTime.now();
        this.lastError = null;
    }

    public void scheduleNextRetry(String errorMessage, long delaySeconds) {
        this.retryCount += 1;
        this.status = SearchThumbnailEnrichmentStatus.PENDING;
        this.nextRetryAt = LocalDateTime.now().plusSeconds(Math.max(0, delaySeconds));
        this.lastError = normalizeError(errorMessage);
    }

    public void markFailed(String errorMessage) {
        this.retryCount += 1;
        this.status = SearchThumbnailEnrichmentStatus.FAILED;
        this.lastError = normalizeError(errorMessage);
    }

    public boolean isProcessing() {
        return this.status == SearchThumbnailEnrichmentStatus.PROCESSING;
    }

    private String normalizeError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "Thumbnail enrichment failed";
        }
        if (errorMessage.length() <= MAX_ERROR_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_LENGTH);
    }
}
