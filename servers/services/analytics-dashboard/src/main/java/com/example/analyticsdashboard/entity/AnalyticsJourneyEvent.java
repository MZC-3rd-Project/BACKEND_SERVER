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
        name = "analytics_journey_event",
        indexes = {
                @Index(name = "idx_analytics_journey_event_event_id", columnList = "event_id"),
                @Index(name = "idx_analytics_journey_event_store_occurred", columnList = "store_id,occurred_at"),
                @Index(name = "idx_analytics_journey_event_seller_occurred", columnList = "seller_id,occurred_at"),
                @Index(name = "idx_analytics_journey_event_order_event", columnList = "order_id,event_type"),
                @Index(name = "idx_analytics_journey_event_domain_event", columnList = "domain_type,event_type,occurred_at")
        }
)
public class AnalyticsJourneyEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 120)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "domain_type", nullable = false, length = 40)
    private String domainType;

    @Column(name = "channel_type", length = 40)
    private String channelType;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "session_id", length = 120)
    private String sessionId;

    @Column(name = "journey_id", length = 120)
    private String journeyId;

    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    @Column(name = "causation_id", length = 120)
    private String causationId;

    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "store_id")
    private Long storeId;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "campaign_id")
    private Long campaignId;

    @Column(name = "hot_deal_id")
    private Long hotDealId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "purchase_id")
    private Long purchaseId;

    @Column(name = "participation_id")
    private Long participationId;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "amount")
    private Long amount;

    @Column(name = "query_hash", length = 128)
    private String queryHash;

    @Column(name = "properties_json", columnDefinition = "TEXT")
    private String propertiesJson;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "ingested_at", nullable = false)
    private LocalDateTime ingestedAt;

    @Builder
    private AnalyticsJourneyEvent(
            String eventId,
            String eventType,
            String domainType,
            String channelType,
            Long userId,
            String sessionId,
            String journeyId,
            String correlationId,
            String causationId,
            Long sellerId,
            Long storeId,
            Long itemId,
            Long campaignId,
            Long hotDealId,
            Long orderId,
            Long purchaseId,
            Long participationId,
            Integer quantity,
            Long amount,
            String queryHash,
            String propertiesJson,
            LocalDateTime occurredAt,
            LocalDateTime ingestedAt
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.domainType = domainType;
        this.channelType = channelType;
        this.userId = userId;
        this.sessionId = sessionId;
        this.journeyId = journeyId;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.sellerId = sellerId;
        this.storeId = storeId;
        this.itemId = itemId;
        this.campaignId = campaignId;
        this.hotDealId = hotDealId;
        this.orderId = orderId;
        this.purchaseId = purchaseId;
        this.participationId = participationId;
        this.quantity = quantity;
        this.amount = amount;
        this.queryHash = queryHash;
        this.propertiesJson = propertiesJson;
        this.occurredAt = occurredAt;
        this.ingestedAt = ingestedAt;
    }
}
