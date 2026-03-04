package com.example.store.entity;


import com.example.core.id.jpa.SnowflakeGenerated;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "store_images")
public class StoreImage {
    @Id
    @SnowflakeGenerated
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Stores store;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_type", nullable = false, length = 30)
    private ImageType imageType;

    @Column(name = "media_id", nullable = false, length = 500)
    private String mediaId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    public static StoreImage create(Stores store, ImageType imageType, String mediaId, int sortOrder) {
        StoreImage si = new StoreImage();
        si.store = store;
        si.imageType = imageType;
        si.mediaId = mediaId;
        si.sortOrder = sortOrder;
        return si;
    }
}
