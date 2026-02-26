package com.example.analyticsdashboard.entity;

import com.example.data.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "analytics_raw_sales_event",
        indexes = {
                @Index(name = "idx_analytics_raw_sales_event_event_id", columnList = "event_id", unique = true),
                @Index(name = "idx_analytics_raw_sales_event_seller_occurred", columnList = "seller_id,occurred_at")
        }
)
public class AnalyticsRawSalesEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 120)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "purchase_id")
    private Long purchaseId;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "gross_amount")
    private Long grossAmount;

    @Column(name = "net_amount")
    private Long netAmount;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "ingested_at", nullable = false)
    private LocalDateTime ingestedAt;

    @Builder
    private AnalyticsRawSalesEvent(
            String eventId,
            String eventType,
            Long sellerId,
            Long itemId,
            Long orderId,
            Long purchaseId,
            Integer quantity,
            Long grossAmount,
            Long netAmount,
            LocalDateTime occurredAt,
            LocalDateTime ingestedAt
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.sellerId = sellerId;
        this.itemId = itemId;
        this.orderId = orderId;
        this.purchaseId = purchaseId;
        this.quantity = quantity;
        this.grossAmount = grossAmount;
        this.netAmount = netAmount;
        this.occurredAt = occurredAt;
        this.ingestedAt = ingestedAt;
    }
}
