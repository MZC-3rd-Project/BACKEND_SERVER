package com.example.hotdeal.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "hot_deals",
        indexes = {
                @Index(name = "idx_hot_deal_item_id", columnList = "itemId"),
                @Index(name = "idx_hot_deal_status", columnList = "status"),
                @Index(name = "idx_hot_deal_start_at", columnList = "startAt"),
                @Index(name = "idx_hot_deal_end_at", columnList = "endAt")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class HotDeal extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "original_price", nullable = false)
    private Long originalPrice;

    @Column(name = "discount_rate", nullable = false)
    private Integer discountRate;

    @Column(name = "discounted_price", nullable = false)
    private Long discountedPrice;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "max_quantity", nullable = false)
    private Integer maxQuantity;

    @Column(name = "max_per_user", nullable = false)
    private Integer maxPerUser;

    @Column(name = "sold_quantity", nullable = false)
    private Integer soldQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HotDealStatus status;

    public static HotDeal create(Long itemId, String title, Long originalPrice,
                                  Integer discountRate, Integer maxQuantity, Integer maxPerUser,
                                  LocalDateTime startAt, LocalDateTime endAt) {
        HotDeal hotDeal = new HotDeal();
        hotDeal.itemId = itemId;
        hotDeal.title = title;
        hotDeal.originalPrice = originalPrice;
        hotDeal.discountRate = discountRate;
        hotDeal.discountedPrice = originalPrice * (100 - discountRate) / 100;
        hotDeal.maxQuantity = maxQuantity;
        hotDeal.maxPerUser = maxPerUser != null ? maxPerUser : 1;
        hotDeal.soldQuantity = 0;
        hotDeal.startAt = startAt;
        hotDeal.endAt = endAt;
        hotDeal.status = HotDealStatus.SCHEDULED;
        return hotDeal;
    }

    public void changeStatus(HotDealStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
    }

    public void activate() {
        changeStatus(HotDealStatus.ACTIVE);
    }

    public void end() {
        changeStatus(HotDealStatus.ENDED);
    }

    public void cancel() {
        changeStatus(HotDealStatus.CANCELLED);
    }

    public void incrementSoldQuantity(int quantity) {
        this.soldQuantity += quantity;
    }

    public int getRemainingQuantity() {
        return maxQuantity - soldQuantity;
    }

    public boolean isSoldOut() {
        return soldQuantity >= maxQuantity;
    }
}
