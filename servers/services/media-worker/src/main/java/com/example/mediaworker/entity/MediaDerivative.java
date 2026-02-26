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

@Entity
@Table(
        name = "media_derivatives",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_media_derivatives_media_profile_version",
                        columnNames = {"media_id", "derivative_profile", "media_version"}
                )
        },
        indexes = {
                @Index(name = "idx_media_derivatives_media_profile", columnList = "media_id,derivative_profile"),
                @Index(name = "idx_media_derivatives_status", columnList = "status")
        }
)
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaDerivative extends BaseEntity {

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

    @Column(name = "object_key", nullable = false, length = 600)
    private String objectKey;

    @Column(name = "url_snapshot", length = 1024)
    private String urlSnapshot;

    @Column(name = "width", nullable = false)
    private Integer width;

    @Column(name = "height", nullable = false)
    private Integer height;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "size", nullable = false)
    private Long size;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MediaDerivativeStatus status;

    public static MediaDerivative createReady(Long mediaId,
                                              MediaDerivativeProfile derivativeProfile,
                                              Long mediaVersion,
                                              String objectKey,
                                              String urlSnapshot,
                                              int width,
                                              int height,
                                              String contentType,
                                              long size) {
        MediaDerivative derivative = new MediaDerivative();
        derivative.mediaId = mediaId;
        derivative.derivativeProfile = derivativeProfile;
        derivative.mediaVersion = mediaVersion;
        derivative.objectKey = objectKey;
        derivative.urlSnapshot = urlSnapshot;
        derivative.width = width;
        derivative.height = height;
        derivative.contentType = contentType;
        derivative.size = size;
        derivative.status = MediaDerivativeStatus.READY;
        return derivative;
    }

    public void replaceWith(String objectKey,
                            String urlSnapshot,
                            int width,
                            int height,
                            String contentType,
                            long size) {
        this.objectKey = objectKey;
        this.urlSnapshot = urlSnapshot;
        this.width = width;
        this.height = height;
        this.contentType = contentType;
        this.size = size;
        this.status = MediaDerivativeStatus.READY;
        restore();
    }
}
