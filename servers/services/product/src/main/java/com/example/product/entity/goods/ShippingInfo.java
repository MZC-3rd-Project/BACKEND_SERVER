package com.example.product.entity.goods;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "shipping_infos", indexes = {
        @Index(name = "idx_shipping_infos_item_id", columnList = "item_id", unique = true)
})
@SQLRestriction("deleted_at IS NULL")
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

    @Column(name = "carrier", length = 100)
    private String carrier;

    @Column(name = "ship_from", length = 255)
    private String shipFrom;

    @Column(name = "return_address", length = 255)
    private String returnAddress;

    @Column(name = "return_shipping_fee")
    private Long returnShippingFee;

    @Column(name = "exchange_shipping_fee")
    private Long exchangeShippingFee;

    @Column(name = "shipping_notice", columnDefinition = "TEXT")
    private String shippingNotice;

    public static ShippingInfo create(Long itemId, Long shippingFee, Long freeShippingThreshold,
                                      int estimatedDays, String returnPolicy) {
        return create(itemId, shippingFee, freeShippingThreshold, estimatedDays, returnPolicy,
                null, null, null, null, null, null);
    }

    public static ShippingInfo create(Long itemId, Long shippingFee, Long freeShippingThreshold,
                                      int estimatedDays, String returnPolicy,
                                      String carrier, String shipFrom, String returnAddress,
                                      Long returnShippingFee, Long exchangeShippingFee,
                                      String shippingNotice) {
        ShippingInfo si = new ShippingInfo();
        si.itemId = itemId;
        si.shippingFee = shippingFee;
        si.freeShippingThreshold = freeShippingThreshold;
        si.estimatedDays = estimatedDays;
        si.returnPolicy = returnPolicy;
        si.carrier = carrier;
        si.shipFrom = shipFrom;
        si.returnAddress = returnAddress;
        si.returnShippingFee = returnShippingFee;
        si.exchangeShippingFee = exchangeShippingFee;
        si.shippingNotice = shippingNotice;
        return si;
    }

    public void update(Long shippingFee, Long freeShippingThreshold, int estimatedDays, String returnPolicy) {
        update(shippingFee, freeShippingThreshold, estimatedDays, returnPolicy,
                this.carrier, this.shipFrom, this.returnAddress,
                this.returnShippingFee, this.exchangeShippingFee, this.shippingNotice);
    }

    public void update(Long shippingFee, Long freeShippingThreshold, int estimatedDays, String returnPolicy,
                       String carrier, String shipFrom, String returnAddress,
                       Long returnShippingFee, Long exchangeShippingFee,
                       String shippingNotice) {
        this.shippingFee = shippingFee;
        this.freeShippingThreshold = freeShippingThreshold;
        this.estimatedDays = estimatedDays;
        this.returnPolicy = returnPolicy;
        this.carrier = carrier;
        this.shipFrom = shipFrom;
        this.returnAddress = returnAddress;
        this.returnShippingFee = returnShippingFee;
        this.exchangeShippingFee = exchangeShippingFee;
        this.shippingNotice = shippingNotice;
    }
}
