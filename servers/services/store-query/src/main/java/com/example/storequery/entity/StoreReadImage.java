package com.example.storequery.entity;

import com.example.data.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "store_read_images")
public class StoreReadImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "store_read_image_id", nullable = false)
    private Long storeReadImageId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_type", nullable = false, length = 30)
    private StoreQueryImageType imageType;

    @Column(name = "media_id", nullable = false)
    private Long mediaId;

    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "source_updated_at", nullable = false)
    private LocalDateTime sourceUpdatedAt;

    @Column(name = "projected_at", nullable = false)
    private LocalDateTime projectedAt;

    public static StoreReadImage of(
        Long storeId,
        StoreQueryImageType imageType,
        Long mediaId,
        String mediaUrl,
        Integer sortOrder,
        LocalDateTime sourceUpdatedAt,
        LocalDateTime projectedAt
    ) {
        return StoreReadImage.builder()
            .storeId(storeId)
            .imageType(imageType)
            .mediaId(mediaId)
            .mediaUrl(mediaUrl)
            .sortOrder(sortOrder == null ? 0 : sortOrder)
            .sourceUpdatedAt(sourceUpdatedAt)
            .projectedAt(projectedAt == null ? LocalDateTime.now() : projectedAt)
            .build();
    }
}
