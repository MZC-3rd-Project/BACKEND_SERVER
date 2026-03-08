package com.example.analyticsdashboard.service.ingest;

import com.example.analyticsdashboard.consumer.item.AnalyticsItemEventMessage;
import com.example.analyticsdashboard.consumer.sales.AnalyticsSalesEventMessage;
import com.example.analyticsdashboard.consumer.search.AnalyticsSearchEventMessage;
import com.example.analyticsdashboard.entity.AnalyticsDimItemSnapshot;
import com.example.analyticsdashboard.entity.AnalyticsRawSalesEvent;
import com.example.analyticsdashboard.entity.AnalyticsRawSearchEvent;
import com.example.analyticsdashboard.repository.AnalyticsDimItemSnapshotRepository;
import com.example.analyticsdashboard.repository.AnalyticsRawSalesEventRepository;
import com.example.analyticsdashboard.repository.AnalyticsRawSearchEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsEventIngestService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
    private static final String ITEM_STATUS_DELETED = "DELETED";
    private static final String ITEM_TYPE_UNKNOWN = "UNKNOWN";

    private final AnalyticsDimItemSnapshotRepository dimItemSnapshotRepository;
    private final AnalyticsRawSalesEventRepository rawSalesEventRepository;
    private final AnalyticsRawSearchEventRepository rawSearchEventRepository;

    @Transactional
    public void ingestItemEvent(AnalyticsItemEventMessage event) {
        if (event == null || event.getItemId() == null) {
            return;
        }

        String eventType = normalize(event.getEventType());
        switch (eventType) {
            case "ITEM_CREATED" -> handleItemCreated(event);
            case "ITEM_UPDATED" -> handleItemUpdated(event);
            case "ITEM_STATUS_CHANGED" -> handleItemStatusChanged(event);
            case "ITEM_DELETED" -> handleItemDeleted(event);
            default -> log.debug("[AnalyticsIngest] skip unsupported item eventType={}", event.getEventType());
        }
    }

    @Transactional
    public void ingestSalesEvent(AnalyticsSalesEventMessage event) {
        if (event == null || event.getEventId() == null || event.getEventType() == null) {
            return;
        }

        String eventType = normalize(event.getEventType());
        Ownership ownership = resolveOwnership(event);
        if (!ownership.resolved()) {
            log.warn("[AnalyticsIngest] sales ownership unresolved. eventId={}, eventType={}, purchaseId={}, itemId={}",
                    event.getEventId(),
                    eventType,
                    event.getPurchaseId(),
                    event.getItemId());
            return;
        }

        Long signedNetAmount = resolveSignedNetAmount(event, eventType, ownership.referenceTotalAmount());
        Long grossAmount = "PURCHASE_CREATED".equals(eventType) ? nullToZero(event.getTotalAmount()) : 0L;

        AnalyticsRawSalesEvent rawEvent = AnalyticsRawSalesEvent.builder()
                .eventId(event.getEventId())
                .eventType(eventType)
                .storeId(ownership.storeId())
                .sellerId(ownership.sellerId())
                .itemId(ownership.itemId())
                .orderId(event.getOrderId())
                .purchaseId(event.getPurchaseId())
                .quantity(event.getQuantity())
                .grossAmount(grossAmount)
                .netAmount(signedNetAmount)
                .occurredAt(parseOccurredAt(event.getOccurredAt()))
                .ingestedAt(LocalDateTime.now())
                .build();
        rawSalesEventRepository.save(rawEvent);
    }

    @Transactional
    public void ingestSearchEvent(AnalyticsSearchEventMessage event) {
        if (event == null || event.getEventId() == null || event.getEventType() == null) {
            return;
        }

        Long resolvedItemId = event.getItemId();
        Long resolvedStoreId = event.getStoreId();
        Long resolvedSellerId = event.getSellerId();

        if ((resolvedStoreId == null || resolvedSellerId == null) && resolvedItemId != null) {
            Optional<AnalyticsDimItemSnapshot> snapshot = dimItemSnapshotRepository.findById(resolvedItemId);
            if (snapshot.isPresent()) {
                AnalyticsDimItemSnapshot itemSnapshot = snapshot.get();
                if (resolvedStoreId == null) {
                    resolvedStoreId = itemSnapshot.getStoreId();
                }
                if (resolvedSellerId == null) {
                    resolvedSellerId = itemSnapshot.getSellerId();
                }
            }
        }

        AnalyticsRawSearchEvent rawEvent = AnalyticsRawSearchEvent.builder()
                .eventId(event.getEventId())
                .eventType(normalize(event.getEventType()))
                .storeId(resolvedStoreId)
                .sellerId(resolvedSellerId)
                .itemId(resolvedItemId)
                .queryHash(event.getQueryHash())
                .sessionId(event.getSessionId())
                .occurredAt(parseOccurredAt(event.getOccurredAt()))
                .ingestedAt(LocalDateTime.now())
                .build();
        rawSearchEventRepository.save(rawEvent);
    }

    private void handleItemCreated(AnalyticsItemEventMessage event) {
        Long storeId = event.getStoreId();
        Long sellerId = event.getSellerId();
        if (storeId == null || sellerId == null) {
            log.warn("[AnalyticsIngest] skip ITEM_CREATED due to missing ownership. itemId={}, storeId={}, sellerId={}",
                    event.getItemId(),
                    storeId,
                    sellerId);
            return;
        }

        Optional<AnalyticsDimItemSnapshot> snapshot = dimItemSnapshotRepository.findById(event.getItemId());
        if (snapshot.isPresent()) {
            AnalyticsDimItemSnapshot existing = snapshot.get();
            existing.updateSnapshot(
                    storeId,
                    sellerId,
                    defaultIfBlank(event.getItemType(), ITEM_TYPE_UNKNOWN),
                    resolveItemStatus(event),
                    event.getPrice(),
                    existing.getStockQuantity(),
                    LocalDateTime.now()
            );
            dimItemSnapshotRepository.save(existing);
            return;
        }

        dimItemSnapshotRepository.save(
                AnalyticsDimItemSnapshot.builder()
                        .itemId(event.getItemId())
                        .storeId(storeId)
                        .sellerId(sellerId)
                        .itemType(defaultIfBlank(event.getItemType(), ITEM_TYPE_UNKNOWN))
                        .itemStatus(resolveItemStatus(event))
                        .price(event.getPrice())
                        .stockQuantity(null)
                        .snapshotAt(LocalDateTime.now())
                        .build()
        );
    }

    private void handleItemUpdated(AnalyticsItemEventMessage event) {
        dimItemSnapshotRepository.findById(event.getItemId()).ifPresent(snapshot -> {
            snapshot.updateSnapshot(
                    snapshot.getStoreId(),
                    snapshot.getSellerId(),
                    defaultIfBlank(snapshot.getItemType(), ITEM_TYPE_UNKNOWN),
                    defaultIfBlank(snapshot.getItemStatus(), "DRAFT"),
                    event.getPrice() != null ? event.getPrice() : snapshot.getPrice(),
                    snapshot.getStockQuantity(),
                    LocalDateTime.now()
            );
            dimItemSnapshotRepository.save(snapshot);
        });
    }

    private void handleItemStatusChanged(AnalyticsItemEventMessage event) {
        dimItemSnapshotRepository.findById(event.getItemId()).ifPresent(snapshot -> {
            snapshot.updateSnapshot(
                    snapshot.getStoreId(),
                    snapshot.getSellerId(),
                    defaultIfBlank(snapshot.getItemType(), ITEM_TYPE_UNKNOWN),
                    defaultIfBlank(event.getNewStatus(), defaultIfBlank(event.getStatus(), snapshot.getItemStatus())),
                    snapshot.getPrice(),
                    snapshot.getStockQuantity(),
                    LocalDateTime.now()
            );
            dimItemSnapshotRepository.save(snapshot);
        });
    }

    private void handleItemDeleted(AnalyticsItemEventMessage event) {
        dimItemSnapshotRepository.findById(event.getItemId()).ifPresent(snapshot -> {
            snapshot.updateSnapshot(
                    snapshot.getStoreId(),
                    snapshot.getSellerId(),
                    defaultIfBlank(snapshot.getItemType(), ITEM_TYPE_UNKNOWN),
                    ITEM_STATUS_DELETED,
                    snapshot.getPrice(),
                    snapshot.getStockQuantity(),
                    LocalDateTime.now()
            );
            dimItemSnapshotRepository.save(snapshot);
        });
    }

    private Ownership resolveOwnership(AnalyticsSalesEventMessage event) {
        Long itemId = event.getItemId();
        Long storeId = event.getStoreId();
        Long sellerId = event.getSellerId();
        Long referenceAmount = event.getTotalAmount();

        if ((storeId == null || sellerId == null || itemId == null) && itemId != null) {
            Optional<AnalyticsDimItemSnapshot> snapshot = dimItemSnapshotRepository.findById(itemId);
            if (snapshot.isPresent()) {
                AnalyticsDimItemSnapshot itemSnapshot = snapshot.get();
                storeId = storeId == null ? itemSnapshot.getStoreId() : storeId;
                sellerId = sellerId == null ? itemSnapshot.getSellerId() : sellerId;
            }
        }

        if ((storeId == null || sellerId == null || itemId == null) && event.getPurchaseId() != null) {
            Optional<AnalyticsRawSalesEvent> previous = rawSalesEventRepository
                    .findTopByPurchaseIdOrderByOccurredAtDesc(event.getPurchaseId());
            if (previous.isPresent()) {
                AnalyticsRawSalesEvent raw = previous.get();
                itemId = itemId == null ? raw.getItemId() : itemId;
                storeId = storeId == null ? raw.getStoreId() : storeId;
                sellerId = sellerId == null ? raw.getSellerId() : sellerId;
                referenceAmount = referenceAmount == null ? raw.getGrossAmount() : referenceAmount;
            }
        }

        return new Ownership(itemId, storeId, sellerId, referenceAmount);
    }

    private Long resolveSignedNetAmount(AnalyticsSalesEventMessage event, String eventType, Long fallbackAmount) {
        Long amount = event.getTotalAmount() != null ? event.getTotalAmount() : fallbackAmount;
        if (amount == null) {
            return null;
        }
        long absolute = Math.abs(amount);
        return "PURCHASE_CREATED".equals(eventType) ? absolute : -absolute;
    }

    private LocalDateTime parseOccurredAt(String rawOccurredAt) {
        if (rawOccurredAt == null || rawOccurredAt.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(rawOccurredAt);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(rawOccurredAt).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Instant.parse(rawOccurredAt).atZone(DEFAULT_ZONE).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }
        return LocalDateTime.now();
    }

    private String resolveItemStatus(AnalyticsItemEventMessage event) {
        if (event.getStatus() != null && !event.getStatus().isBlank()) {
            return event.getStatus();
        }
        if (event.getNewStatus() != null && !event.getNewStatus().isBlank()) {
            return event.getNewStatus();
        }
        return "DRAFT";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String defaultIfBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private Long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private record Ownership(Long itemId, Long storeId, Long sellerId, Long referenceTotalAmount) {
        private boolean resolved() {
            return itemId != null && storeId != null && sellerId != null;
        }
    }
}
