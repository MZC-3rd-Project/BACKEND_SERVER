package com.example.store.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "stores")
@Builder
@AllArgsConstructor
public class Stores extends BaseEntity {
    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "store_name")
    private String storeName;

    @Enumerated(EnumType.STRING)
    private StoreStatus status;

    public static Stores create(Long userId, String storeName) {
        Stores store = new Stores();
        store.userId = userId;
        store.storeName = storeName;
        store.status = StoreStatus.INACTIVE;
        return store;
    }

}
