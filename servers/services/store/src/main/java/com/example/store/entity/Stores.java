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

    public static Stores of(Long userId, String storeName) {
        return Stores.builder()
            .userId(userId)
            .storeName(storeName)
            .status(StoreStatus.ACTIVE)
            .build();
    }
    public void updateStore(String storeName, StoreStatus status) {
        if(storeName != null)this.storeName = storeName;
        if(status != null) this.status = status;
    }



}
