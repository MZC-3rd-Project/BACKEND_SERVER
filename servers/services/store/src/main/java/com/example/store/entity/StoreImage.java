package com.example.store.entity;


import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import com.example.store.repository.StoreImageRepository;
import jakarta.persistence.*;
import lombok.*;
import org.apache.catalina.Store;
import org.hibernate.annotations.SQLRestriction;

@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "store_images")
public class StoreImage extends BaseEntity {
    @Id
    @SnowflakeGenerated
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Stores store;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_type", nullable = false, length = 30)
    private ImageType imageType;

    @Column(name = "media_id", nullable = true)
    private Long mediaId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    public static StoreImage of(Stores store, ImageType imageType, Long mediaId, int sortOrder) {
       return StoreImage.builder()
           .store(store)
           .mediaId(mediaId)
           .imageType(imageType)
           .sortOrder(sortOrder)
           .build();
    }

    public static StoreImage of(ImageType imageType, Long mediaId, int sortOrder) {
        return StoreImage.builder()
            .mediaId(mediaId)
            .imageType(imageType)
            .sortOrder(sortOrder)
            .build();
    }

}
