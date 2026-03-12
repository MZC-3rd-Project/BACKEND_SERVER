package com.example.storequery.entity;

import com.example.data.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "store_read_models")
public class StoreReadModel extends BaseEntity {

    @Id
    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "store_name", nullable = false, length = 120)
    private String storeName;

    @Column(name = "owner_nickname", length = 100)
    private String ownerNickname;

    @Column(name = "owner_profile_image_url", length = 500)
    private String ownerProfileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StoreQueryStatus status;

    @Column(name = "description")
    private String description;

    @Column(name = "default_address", length = 400)
    private String defaultAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_address_type", length = 30)
    private StoreQueryAddressType defaultAddressType;

    @Column(name = "primary_contact_value", length = 100)
    private String primaryContactValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_contact_type", length = 30)
    private StoreQueryContactType primaryContactType;

    @Column(name = "thumbnail_media_id")
    private Long thumbnailMediaId;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "thumbnail_sort_order")
    private Integer thumbnailSortOrder;

    @Builder.Default
    @Column(name = "gallery_count", nullable = false)
    private Integer galleryCount = 0;

    @Builder.Default
    @Column(name = "active_item_count", nullable = false)
    private Integer activeItemCount = 0;

    @Column(name = "latest_item_updated_at")
    private LocalDateTime latestItemUpdatedAt;

    @Column(name = "search_text")
    private String searchText;

    @Column(name = "search_tsv", insertable = false, updatable = false, columnDefinition = "tsvector")
    private String searchTsv;

    @Column(name = "source_created_at", nullable = false)
    private LocalDateTime sourceCreatedAt;

    @Column(name = "source_updated_at", nullable = false)
    private LocalDateTime sourceUpdatedAt;

    @Column(name = "projected_at", nullable = false)
    private LocalDateTime projectedAt;

    public static StoreReadModel of(
        Long storeId,
        Long userId,
        String storeName,
        String ownerNickname,
        String ownerProfileImageUrl,
        StoreQueryStatus status,
        String description,
        String defaultAddress,
        StoreQueryAddressType defaultAddressType,
        String primaryContactValue,
        StoreQueryContactType primaryContactType,
        Long thumbnailMediaId,
        String thumbnailUrl,
        Integer thumbnailSortOrder,
        Integer galleryCount,
        Integer activeItemCount,
        LocalDateTime latestItemUpdatedAt,
        String searchText,
        LocalDateTime sourceCreatedAt,
        LocalDateTime sourceUpdatedAt,
        LocalDateTime projectedAt
    ) {
        return StoreReadModel.builder()
            .storeId(storeId)
            .userId(userId)
            .storeName(storeName)
            .ownerNickname(ownerNickname)
            .ownerProfileImageUrl(ownerProfileImageUrl)
            .status(status)
            .description(description)
            .defaultAddress(defaultAddress)
            .defaultAddressType(defaultAddressType)
            .primaryContactValue(primaryContactValue)
            .primaryContactType(primaryContactType)
            .thumbnailMediaId(thumbnailMediaId)
            .thumbnailUrl(thumbnailUrl)
            .thumbnailSortOrder(thumbnailSortOrder)
            .galleryCount(galleryCount == null ? 0 : galleryCount)
            .activeItemCount(activeItemCount == null ? 0 : activeItemCount)
            .latestItemUpdatedAt(latestItemUpdatedAt)
            .searchText(searchText)
            .sourceCreatedAt(sourceCreatedAt)
            .sourceUpdatedAt(sourceUpdatedAt)
            .projectedAt(projectedAt == null ? LocalDateTime.now() : projectedAt)
            .build();
    }

    public void restoreForProjection() {
        restore();
    }

    public void project(
        Long userId,
        String storeName,
        String ownerNickname,
        String ownerProfileImageUrl,
        StoreQueryStatus status,
        String description,
        String defaultAddress,
        StoreQueryAddressType defaultAddressType,
        String primaryContactValue,
        StoreQueryContactType primaryContactType,
        Long thumbnailMediaId,
        String thumbnailUrl,
        Integer thumbnailSortOrder,
        Integer galleryCount,
        Integer activeItemCount,
        LocalDateTime latestItemUpdatedAt,
        String searchText,
        LocalDateTime sourceCreatedAt,
        LocalDateTime sourceUpdatedAt,
        LocalDateTime projectedAt
    ) {
        this.userId = userId;
        this.storeName = storeName;
        this.ownerNickname = ownerNickname;
        this.ownerProfileImageUrl = ownerProfileImageUrl;
        this.status = status;
        this.description = description;
        this.defaultAddress = defaultAddress;
        this.defaultAddressType = defaultAddressType;
        this.primaryContactValue = primaryContactValue;
        this.primaryContactType = primaryContactType;
        this.thumbnailMediaId = thumbnailMediaId;
        this.thumbnailUrl = thumbnailUrl;
        this.thumbnailSortOrder = thumbnailSortOrder;
        this.galleryCount = galleryCount == null ? 0 : galleryCount;
        this.activeItemCount = activeItemCount == null ? 0 : activeItemCount;
        this.latestItemUpdatedAt = latestItemUpdatedAt;
        this.searchText = searchText;
        this.sourceCreatedAt = sourceCreatedAt;
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.projectedAt = projectedAt == null ? LocalDateTime.now() : projectedAt;
    }
}
