package com.example.product.domain.goods;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shipping_infos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShippingInfo extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "item_id", nullable = false, unique = true)
    private Long itemId;

    @Column(name = "shipping_fee", nullable = false)
    private Long shippingFee;

    @Column(name = "free_shipping_threshold")
    private Long freeShippingThreshold;

    @Column(name = "estimated_days", nullable = false)
    private Integer estimatedDays;

    @Column(name = "return_policy", columnDefinition = "TEXT")
    private String returnPolicy;

    public static ShippingInfo create(Long itemId, Long shippingFee, Long freeShippingThreshold,
                                      int estimatedDays, String returnPolicy) {
        ShippingInfo si = new ShippingInfo();
        si.itemId = itemId;
        si.shippingFee = shippingFee;
        si.freeShippingThreshold = freeShippingThreshold;
        si.estimatedDays = estimatedDays;
        si.returnPolicy = returnPolicy;
        return si;
    }

    public void update(Long shippingFee, Long freeShippingThreshold, int estimatedDays, String returnPolicy) {
        this.shippingFee = shippingFee;
        this.freeShippingThreshold = freeShippingThreshold;
        this.estimatedDays = estimatedDays;
        this.returnPolicy = returnPolicy;
    }
}
