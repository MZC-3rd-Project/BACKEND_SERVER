package com.example.mediaworker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "media_files")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaFileRecord {

    @Id
    private Long id;

    @Column(name = "bucket_name", nullable = false, length = 120)
    private String bucketName;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MediaFileStatus status;

    public boolean isReadyForDerivative() {
        return status == MediaFileStatus.CONFIRMED || status == MediaFileStatus.READY;
    }

    public void markReady() {
        status = MediaFileStatus.READY;
    }
}
