package com.example.store.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Entity
@Table(name = "store_addresses")
@Builder
public class StoreAddress extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Stores store;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 30)
    private AddressType addressType;

    @Column(name = "address", nullable = false, length = 400)
    private String address;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    public static StoreAddress create(Stores store, AddressType addressType, String address, boolean isDefault) {
        StoreAddress sa = new StoreAddress();
        sa.store = store;
        sa.addressType = addressType;
        sa.address = address;
        sa.isDefault = isDefault;
        return sa;
    }
}
