package com.example.media.entity;

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
@Table(name = "media_links", indexes = {
        @Index(name = "idx_media_links_media_id", columnList = "media_id"),
        @Index(name = "idx_media_links_owner_usage_sort", columnList = "owner_type,owner_id,usage_type,sort_order"),
        @Index(name = "idx_media_links_owner_type_id", columnList = "owner_type,owner_id")
})
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaLink extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "media_id", nullable = false)
    private Long mediaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 40)
    private MediaOwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false, length = 40)
    private MediaUsageType usageType;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    public static MediaLink create(Long mediaId,
                                   MediaOwnerType ownerType,
                                   Long ownerId,
                                   MediaUsageType usageType,
                                   int sortOrder) {
        MediaLink link = new MediaLink();
        link.mediaId = mediaId;
        link.ownerType = ownerType;
        link.ownerId = ownerId;
        link.usageType = usageType;
        link.sortOrder = sortOrder;
        return link;
    }

    public boolean matchesMedia(Long targetMediaId) {
        return mediaId != null && mediaId.equals(targetMediaId);
    }

    public void updateSortOrder(int newSortOrder) {
        this.sortOrder = newSortOrder;
    }

    public void updateUsageType(MediaUsageType newUsageType) {
        this.usageType = newUsageType;
    }
}
