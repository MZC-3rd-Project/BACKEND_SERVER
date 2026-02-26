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
        name = "analytics_raw_search_event",
        indexes = {
                @Index(name = "idx_analytics_raw_search_event_event_id", columnList = "event_id", unique = true),
                @Index(name = "idx_analytics_raw_search_event_seller_occurred", columnList = "seller_id,occurred_at"),
                @Index(name = "idx_analytics_raw_search_event_item_occurred", columnList = "item_id,occurred_at")
        }
)
public class AnalyticsRawSearchEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 120)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "query_hash", length = 128)
    private String queryHash;

    @Column(name = "session_id", length = 120)
    private String sessionId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "ingested_at", nullable = false)
    private LocalDateTime ingestedAt;

    @Builder
    private AnalyticsRawSearchEvent(
            String eventId,
            String eventType,
            Long sellerId,
            Long itemId,
            String queryHash,
            String sessionId,
            LocalDateTime occurredAt,
            LocalDateTime ingestedAt
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.sellerId = sellerId;
        this.itemId = itemId;
        this.queryHash = queryHash;
        this.sessionId = sessionId;
        this.occurredAt = occurredAt;
        this.ingestedAt = ingestedAt;
    }
}
