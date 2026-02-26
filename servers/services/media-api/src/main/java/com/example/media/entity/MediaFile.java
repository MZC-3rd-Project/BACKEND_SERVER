package com.example.media.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "media_files", indexes = {
        @Index(name = "idx_media_files_status", columnList = "status"),
        @Index(name = "idx_media_files_uploader_id", columnList = "uploader_id"),
        @Index(name = "idx_media_files_token_expires", columnList = "upload_token_expires_at")
})
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaFile extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "uploader_id")
    private Long uploaderId;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "object_key", nullable = false, unique = true, length = 500)
    private String objectKey;

    @Column(name = "bucket_name", nullable = false, length = 120)
    private String bucketName;

    @Column(name = "requested_content_type", nullable = false, length = 100)
    private String requestedContentType;

    @Column(name = "requested_file_size", nullable = false)
    private Long requestedFileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_owner_type", length = 40)
    private MediaOwnerType requestedOwnerType;

    @Column(name = "requested_owner_id")
    private Long requestedOwnerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_usage_type", length = 40)
    private MediaUsageType requestedUsageType;

    @Column(name = "requested_sort_order")
    private Integer requestedSortOrder;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "etag", length = 128)
    private String etag;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MediaStatus status;

    @Column(name = "upload_token", nullable = false, length = 100)
    private String uploadToken;

    @Column(name = "upload_token_expires_at", nullable = false)
    private LocalDateTime uploadTokenExpiresAt;

    @Column(name = "token_used_at")
    private LocalDateTime tokenUsedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "last_error_reason", length = 500)
    private String lastErrorReason;

    public static MediaFile createPending(Long uploaderId,
                                          String originalFileName,
                                          String objectKey,
                                          String bucketName,
                                          String requestedContentType,
                                          Long requestedFileSize,
                                          MediaOwnerType requestedOwnerType,
                                          Long requestedOwnerId,
                                          MediaUsageType requestedUsageType,
                                          Integer requestedSortOrder,
                                          String uploadToken,
                                          LocalDateTime uploadTokenExpiresAt) {
        MediaFile mediaFile = new MediaFile();
        mediaFile.uploaderId = uploaderId;
        mediaFile.originalFileName = originalFileName;
        mediaFile.objectKey = objectKey;
        mediaFile.bucketName = bucketName;
        mediaFile.requestedContentType = requestedContentType;
        mediaFile.requestedFileSize = requestedFileSize;
        mediaFile.requestedOwnerType = requestedOwnerType;
        mediaFile.requestedOwnerId = requestedOwnerId;
        mediaFile.requestedUsageType = requestedUsageType;
        mediaFile.requestedSortOrder = requestedSortOrder;
        mediaFile.status = MediaStatus.PENDING_UPLOAD;
        mediaFile.uploadToken = uploadToken;
        mediaFile.uploadTokenExpiresAt = uploadTokenExpiresAt;
        return mediaFile;
    }

    public boolean isOwnedBy(Long userId) {
        if (uploaderId == null || userId == null) {
            return false;
        }
        return uploaderId.equals(userId);
    }

    public boolean hasSameUploadToken(String token) {
        return uploadToken != null && uploadToken.equals(token);
    }

    public boolean isUploadTokenExpired(LocalDateTime now) {
        return uploadTokenExpiresAt == null || uploadTokenExpiresAt.isBefore(now);
    }

    public boolean isAlreadyConfirmed() {
        return status == MediaStatus.CONFIRMED || status == MediaStatus.READY;
    }

    public void confirm(long actualFileSize, String actualContentType, String actualEtag, LocalDateTime confirmedAt) {
        this.fileSize = actualFileSize;
        this.contentType = actualContentType;
        this.etag = actualEtag;
        this.status = MediaStatus.CONFIRMED;
        this.confirmedAt = confirmedAt;
        this.tokenUsedAt = confirmedAt;
        this.lastErrorReason = null;
    }

    public void markFailed(String reason) {
        this.status = MediaStatus.FAILED;
        this.lastErrorReason = reason;
    }

    public void markExpired() {
        this.status = MediaStatus.EXPIRED;
    }

    public void markReady() {
        this.status = MediaStatus.READY;
    }

    public void markDeleted() {
        this.status = MediaStatus.DELETED;
        softDelete();
    }

    public boolean hasRequestedBinding() {
        return requestedOwnerType != null && requestedOwnerId != null;
    }
}
