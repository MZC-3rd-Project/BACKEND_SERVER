package com.example.analyticsdashboard.service.ingest;

import com.example.analyticsdashboard.consumer.funding.AnalyticsFundingEventMessage;
import com.example.analyticsdashboard.consumer.hotdeal.AnalyticsHotDealEventMessage;
import com.example.analyticsdashboard.consumer.item.AnalyticsItemEventMessage;
import com.example.analyticsdashboard.consumer.order.AnalyticsOrderEventMessage;
import com.example.analyticsdashboard.consumer.sales.AnalyticsSalesEventMessage;
import com.example.analyticsdashboard.consumer.search.AnalyticsSearchEventMessage;
import com.example.analyticsdashboard.entity.AnalyticsDimItemSnapshot;
import com.example.analyticsdashboard.entity.AnalyticsJourneyEvent;
import com.example.analyticsdashboard.entity.AnalyticsRawSalesEvent;
import com.example.analyticsdashboard.entity.AnalyticsRawSearchEvent;
import com.example.analyticsdashboard.repository.AnalyticsDimItemSnapshotRepository;
import com.example.analyticsdashboard.repository.AnalyticsJourneyEventRepository;
import com.example.analyticsdashboard.repository.AnalyticsRawSalesEventRepository;
import com.example.analyticsdashboard.repository.AnalyticsRawSearchEventRepository;
import com.example.core.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsEventIngestService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
    private static final String ITEM_STATUS_DELETED = "DELETED";
    private static final String ITEM_TYPE_UNKNOWN = "UNKNOWN";

    private static final String DOMAIN_SEARCH = "SEARCH";
    private static final String DOMAIN_NORMAL = "NORMAL";
    private static final String DOMAIN_FUNDING = "FUNDING";
    private static final String DOMAIN_HOT_DEAL = "HOT_DEAL";
    private static final String DOMAIN_ORDER = "ORDER";

    private static final String ORDER_CREATED_EVENT = "ORDER_CREATED_EVENT";
    private static final String SEARCH_EXECUTED_EVENT = "SEARCH_EXECUTED";

    private final AnalyticsDimItemSnapshotRepository dimItemSnapshotRepository;
    private final AnalyticsRawSalesEventRepository rawSalesEventRepository;
    private final AnalyticsRawSearchEventRepository rawSearchEventRepository;
    private final AnalyticsJourneyEventRepository journeyEventRepository;

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
        if (rawSalesEventRepository.findByEventId(event.getEventId()).isPresent()) {
            log.debug("[AnalyticsIngest] skip duplicate sales event. eventId={}", event.getEventId());
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

        LocalDateTime occurredAt = parseOccurredAt(event.getOccurredAt());
        LocalDateTime ingestedAt = LocalDateTime.now();
        Long signedNetAmount = resolveSignedNetAmount(event, eventType, ownership.referenceTotalAmount());
        Long grossAmount = "PURCHASE_CREATED".equals(eventType) ? nullToZero(event.getTotalAmount()) : 0L;
        String resolvedJourneyId = resolveJourneyId(
                event.getJourneyId(),
                event.getCorrelationId(),
                event.getSessionId(),
                event.getUserId(),
                event.getOrderId(),
                null,
                null,
                event.getPurchaseId()
        );

        AnalyticsRawSalesEvent rawEvent = AnalyticsRawSalesEvent.builder()
                .eventId(event.getEventId())
                .eventType(eventType)
                .storeId(ownership.storeId())
                .sellerId(ownership.sellerId())
                .itemId(ownership.itemId())
                .orderId(event.getOrderId())
                .purchaseId(event.getPurchaseId())
                .userId(event.getUserId())
                .sessionId(event.getSessionId())
                .journeyId(resolvedJourneyId)
                .correlationId(event.getCorrelationId())
                .causationId(event.getCausationId())
                .quantity(event.getQuantity())
                .grossAmount(grossAmount)
                .netAmount(signedNetAmount)
                .occurredAt(occurredAt)
                .ingestedAt(ingestedAt)
                .build();
        rawSalesEventRepository.save(rawEvent);

        saveJourneyEventIfAbsent(AnalyticsJourneyEvent.builder()
                .eventId(event.getEventId())
                .eventSequence(0)
                .eventType(eventType)
                .domainType(DOMAIN_NORMAL)
                .channelType(DOMAIN_NORMAL)
                .userId(event.getUserId())
                .sessionId(event.getSessionId())
                .journeyId(resolvedJourneyId)
                .correlationId(event.getCorrelationId())
                .causationId(event.getCausationId())
                .sellerId(ownership.sellerId())
                .storeId(ownership.storeId())
                .itemId(ownership.itemId())
                .orderId(event.getOrderId())
                .purchaseId(event.getPurchaseId())
                .quantity(event.getQuantity())
                .amount(event.getTotalAmount())
                .occurredAt(occurredAt)
                .ingestedAt(ingestedAt)
                .build());
    }

    @Transactional
    public void ingestSearchEvent(AnalyticsSearchEventMessage event) {
        if (event == null || event.getEventId() == null || event.getEventType() == null) {
            return;
        }
        if (rawSearchEventRepository.findByEventId(event.getEventId()).isPresent()) {
            log.debug("[AnalyticsIngest] skip duplicate search event. eventId={}", event.getEventId());
            return;
        }

        String eventType = normalize(event.getEventType());
        Ownership rawOwnership = resolveOwnership(event.getItemId(), event.getStoreId(), event.getSellerId());

        LocalDateTime occurredAt = parseOccurredAt(event.getOccurredAt());
        LocalDateTime ingestedAt = LocalDateTime.now();
        String resolvedJourneyId = resolveJourneyId(
                event.getJourneyId(),
                event.getCorrelationId(),
                event.getSessionId(),
                event.getUserId(),
                null,
                null,
                null,
                null
        );
        String propertiesJson = buildSearchPropertiesJson(event);

        AnalyticsRawSearchEvent rawEvent = AnalyticsRawSearchEvent.builder()
                .eventId(event.getEventId())
                .eventType(eventType)
                .storeId(rawOwnership.storeId())
                .sellerId(rawOwnership.sellerId())
                .itemId(rawOwnership.itemId())
                .queryHash(event.getQueryHash())
                .userId(event.getUserId())
                .sessionId(event.getSessionId())
                .journeyId(resolvedJourneyId)
                .correlationId(event.getCorrelationId())
                .causationId(event.getCausationId())
                .occurredAt(occurredAt)
                .ingestedAt(ingestedAt)
                .build();
        rawSearchEventRepository.save(rawEvent);

        List<SearchJourneyOwnership> journeyOwnerships = SEARCH_EXECUTED_EVENT.equals(eventType)
                ? resolveSearchJourneyOwnerships(event, rawOwnership)
                : List.of(SearchJourneyOwnership.of(rawOwnership.itemId(), rawOwnership.storeId(), rawOwnership.sellerId()));

        if (journeyOwnerships.isEmpty()) {
            saveJourneyEventIfAbsent(buildSearchJourneyEvent(
                    event,
                    eventType,
                    occurredAt,
                    ingestedAt,
                    resolvedJourneyId,
                    0,
                    null,
                    null,
                    null,
                    propertiesJson
            ));
            return;
        }

        for (int index = 0; index < journeyOwnerships.size(); index++) {
            SearchJourneyOwnership ownership = journeyOwnerships.get(index);
            saveJourneyEventIfAbsent(buildSearchJourneyEvent(
                    event,
                    eventType,
                    occurredAt,
                    ingestedAt,
                    resolvedJourneyId,
                    index,
                    ownership.itemId(),
                    ownership.storeId(),
                    ownership.sellerId(),
                    propertiesJson
            ));
        }
    }

    @Transactional
    public void ingestOrderEvent(AnalyticsOrderEventMessage event) {
        if (event == null || event.getEventId() == null || event.getEventType() == null || event.getOrderId() == null) {
            return;
        }

        String eventType = normalize(event.getEventType());
        LocalDateTime occurredAt = parseOccurredAt(event.getOccurredAt());
        if (ORDER_CREATED_EVENT.equals(eventType)) {
            ingestOrderCreatedEvent(event, occurredAt);
            return;
        }
        ingestOrderLifecycleEvent(event, eventType, occurredAt);
    }

    @Transactional
    public void ingestFundingEvent(AnalyticsFundingEventMessage event) {
        if (event == null || event.getEventId() == null || event.getEventType() == null || event.getCampaignId() == null) {
            return;
        }

        Ownership ownership = resolveOwnership(event.getItemId(), null, event.getSellerId());
        LocalDateTime occurredAt = parseOccurredAt(event.getOccurredAt());
        LocalDateTime ingestedAt = LocalDateTime.now();

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("fundingType", event.getFundingType());

        saveJourneyEventIfAbsent(AnalyticsJourneyEvent.builder()
                .eventId(event.getEventId())
                .eventSequence(0)
                .eventType(normalize(event.getEventType()))
                .domainType(DOMAIN_FUNDING)
                .channelType(DOMAIN_FUNDING)
                .userId(event.getUserId())
                .journeyId(resolveJourneyId(
                        null,
                        null,
                        null,
                        event.getUserId(),
                        event.getOrderId(),
                        event.getCampaignId(),
                        null,
                        null
                ))
                .sellerId(ownership.sellerId())
                .storeId(ownership.storeId())
                .itemId(ownership.itemId())
                .campaignId(event.getCampaignId())
                .orderId(event.getOrderId())
                .participationId(event.getParticipationId())
                .quantity(event.getQuantity())
                .amount(event.getAmount())
                .propertiesJson(toJson(properties))
                .occurredAt(occurredAt)
                .ingestedAt(ingestedAt)
                .build());
    }

    @Transactional
    public void ingestHotDealEvent(AnalyticsHotDealEventMessage event) {
        if (event == null || event.getEventId() == null || event.getEventType() == null) {
            return;
        }

        Ownership ownership = resolveOwnership(event.getItemId(), null, event.getSellerId());
        LocalDateTime occurredAt = parseOccurredAt(event.getOccurredAt());
        LocalDateTime ingestedAt = LocalDateTime.now();

        Map<String, Object> properties = new LinkedHashMap<>();
        putIfPresent(properties, "title", event.getTitle());
        putIfPresent(properties, "discountedPrice", event.getDiscountedPrice());
        putIfPresent(properties, "discountRate", event.getDiscountRate());
        putIfPresent(properties, "maxQuantity", event.getMaxQuantity());

        saveJourneyEventIfAbsent(AnalyticsJourneyEvent.builder()
                .eventId(event.getEventId())
                .eventSequence(0)
                .eventType(normalize(event.getEventType()))
                .domainType(DOMAIN_HOT_DEAL)
                .channelType(DOMAIN_HOT_DEAL)
                .userId(event.getUserId())
                .journeyId(resolveJourneyId(
                        null,
                        null,
                        null,
                        event.getUserId(),
                        event.getOrderId(),
                        null,
                        event.getHotDealId(),
                        null
                ))
                .sellerId(ownership.sellerId())
                .storeId(ownership.storeId())
                .itemId(ownership.itemId())
                .hotDealId(event.getHotDealId())
                .orderId(event.getOrderId())
                .quantity(event.getQuantity())
                .amount(event.getTotalAmount())
                .propertiesJson(toJson(properties))
                .occurredAt(occurredAt)
                .ingestedAt(ingestedAt)
                .build());
    }

    private void ingestOrderCreatedEvent(AnalyticsOrderEventMessage event, LocalDateTime occurredAt) {
        LocalDateTime ingestedAt = LocalDateTime.now();
        List<AnalyticsOrderEventMessage.OrderItemPayload> items =
                event.getItems() == null ? List.of() : event.getItems();
        if (items.isEmpty()) {
            saveJourneyEventIfAbsent(buildOrderJourneyEvent(
                    event,
                    0,
                    occurredAt,
                    ingestedAt,
                    DOMAIN_ORDER,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    event.getTotalAmount(),
                    null,
                    null
            ));
            return;
        }

        int eventSequence = 0;
        for (AnalyticsOrderEventMessage.OrderItemPayload item : items) {
            Ownership ownership = resolveOwnership(item.getItemId(), item.getStoreId(), null);
            String channelType = normalizeChannelType(item.getChannelType());
            String domainType = resolveOrderDomainType(channelType);

            Map<String, Object> properties = new LinkedHashMap<>();
            putIfPresent(properties, "titleSnap", item.getTitleSnap());
            putIfPresent(properties, "itemTypeSnap", item.getItemTypeSnap());
            putIfPresent(properties, "unitPrice", item.getUnitPrice());

            saveJourneyEventIfAbsent(buildOrderJourneyEvent(
                    event,
                    eventSequence,
                    occurredAt,
                    ingestedAt,
                    domainType,
                    channelType,
                    ownership.storeId(),
                    ownership.sellerId(),
                    ownership.itemId(),
                    resolveCampaignId(channelType, item.getChannelRefId()),
                    resolveHotDealId(channelType, item.getChannelRefId()),
                    item.getQuantity(),
                    firstNonNull(item.getLineAmount(), item.getUnitPrice(), event.getTotalAmount()),
                    null,
                    toJson(properties)
            ));
            eventSequence++;
        }
    }

    private void ingestOrderLifecycleEvent(AnalyticsOrderEventMessage event, String eventType, LocalDateTime occurredAt) {
        LocalDateTime ingestedAt = LocalDateTime.now();
        List<AnalyticsJourneyEvent> createdEvents =
                journeyEventRepository.findByOrderIdAndEventTypeOrderByOccurredAtAsc(event.getOrderId(), ORDER_CREATED_EVENT);
        if (createdEvents.isEmpty()) {
            saveJourneyEventIfAbsent(buildOrderJourneyEvent(
                    event,
                    0,
                    occurredAt,
                    ingestedAt,
                    DOMAIN_ORDER,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    event.getTotalAmount(),
                    eventType,
                    null
            ));
            return;
        }

        for (int index = 0; index < createdEvents.size(); index++) {
            AnalyticsJourneyEvent createdEvent = createdEvents.get(index);
            saveJourneyEventIfAbsent(AnalyticsJourneyEvent.builder()
                    .eventId(event.getEventId())
                    .eventSequence(index)
                    .eventType(eventType)
                    .domainType(defaultIfBlank(createdEvent.getDomainType(), DOMAIN_ORDER))
                    .channelType(createdEvent.getChannelType())
                    .userId(event.getUserId() != null ? event.getUserId() : createdEvent.getUserId())
                    .sessionId(createdEvent.getSessionId())
                    .journeyId(resolveJourneyId(
                            createdEvent.getJourneyId(),
                            createdEvent.getCorrelationId(),
                            createdEvent.getSessionId(),
                            event.getUserId() != null ? event.getUserId() : createdEvent.getUserId(),
                            event.getOrderId(),
                            createdEvent.getCampaignId(),
                            createdEvent.getHotDealId(),
                            createdEvent.getPurchaseId()
                    ))
                    .correlationId(createdEvent.getCorrelationId())
                    .causationId(createdEvent.getCausationId())
                    .sellerId(createdEvent.getSellerId())
                    .storeId(createdEvent.getStoreId())
                    .itemId(createdEvent.getItemId())
                    .campaignId(createdEvent.getCampaignId())
                    .hotDealId(createdEvent.getHotDealId())
                    .orderId(event.getOrderId())
                    .purchaseId(createdEvent.getPurchaseId())
                    .participationId(createdEvent.getParticipationId())
                    .quantity(createdEvent.getQuantity())
                    .amount(firstNonNull(event.getTotalAmount(), createdEvent.getAmount(), null))
                    .queryHash(createdEvent.getQueryHash())
                    .propertiesJson(createdEvent.getPropertiesJson())
                    .occurredAt(occurredAt)
                    .ingestedAt(ingestedAt)
                    .build());
        }
    }

    private AnalyticsJourneyEvent buildOrderJourneyEvent(
            AnalyticsOrderEventMessage event,
            int eventSequence,
            LocalDateTime occurredAt,
            LocalDateTime ingestedAt,
            String domainType,
            String channelType,
            Long storeId,
            Long sellerId,
            Long itemId,
            Long campaignId,
            Long hotDealId,
            Integer quantity,
            Long amount,
            String overrideEventType,
            String propertiesJson
    ) {
        return AnalyticsJourneyEvent.builder()
                .eventId(event.getEventId())
                .eventSequence(eventSequence)
                .eventType(defaultIfBlank(overrideEventType, normalize(event.getEventType())))
                .domainType(domainType)
                .channelType(channelType)
                .userId(event.getUserId())
                .journeyId(resolveJourneyId(
                        null,
                        null,
                        null,
                        event.getUserId(),
                        event.getOrderId(),
                        campaignId,
                        hotDealId,
                        null
                ))
                .sellerId(sellerId)
                .storeId(storeId)
                .itemId(itemId)
                .campaignId(campaignId)
                .hotDealId(hotDealId)
                .orderId(event.getOrderId())
                .quantity(quantity)
                .amount(amount)
                .propertiesJson(propertiesJson)
                .occurredAt(occurredAt)
                .ingestedAt(ingestedAt)
                .build();
    }

    private void saveJourneyEventIfAbsent(AnalyticsJourneyEvent journeyEvent) {
        if (journeyEvent == null || journeyEvent.getEventId() == null || journeyEvent.getEventSequence() == null) {
            return;
        }
        if (journeyEventRepository.existsByEventIdAndEventSequence(
                journeyEvent.getEventId(),
                journeyEvent.getEventSequence()
        )) {
            log.debug(
                    "[AnalyticsIngest] skip duplicate journey event. eventId={}, eventSequence={}",
                    journeyEvent.getEventId(),
                    journeyEvent.getEventSequence()
            );
            return;
        }
        journeyEventRepository.save(journeyEvent);
    }

    private AnalyticsJourneyEvent buildSearchJourneyEvent(
            AnalyticsSearchEventMessage event,
            String eventType,
            LocalDateTime occurredAt,
            LocalDateTime ingestedAt,
            String journeyId,
            int eventSequence,
            Long itemId,
            Long storeId,
            Long sellerId,
            String propertiesJson
    ) {
        return AnalyticsJourneyEvent.builder()
                .eventId(event.getEventId())
                .eventSequence(eventSequence)
                .eventType(eventType)
                .domainType(DOMAIN_SEARCH)
                .channelType(null)
                .userId(event.getUserId())
                .sessionId(event.getSessionId())
                .journeyId(journeyId)
                .correlationId(event.getCorrelationId())
                .causationId(event.getCausationId())
                .sellerId(sellerId)
                .storeId(storeId)
                .itemId(itemId)
                .queryHash(event.getQueryHash())
                .propertiesJson(propertiesJson)
                .occurredAt(occurredAt)
                .ingestedAt(ingestedAt)
                .build();
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

    private Ownership resolveOwnership(Long itemId, Long storeId, Long sellerId) {
        if ((storeId != null && sellerId != null) || itemId == null) {
            return new Ownership(itemId, storeId, sellerId, null);
        }

        return dimItemSnapshotRepository.findById(itemId)
                .map(snapshot -> new Ownership(
                        itemId,
                        storeId != null ? storeId : snapshot.getStoreId(),
                        sellerId != null ? sellerId : snapshot.getSellerId(),
                        null
                ))
                .orElse(new Ownership(itemId, storeId, sellerId, null));
    }

    private List<SearchJourneyOwnership> resolveSearchJourneyOwnerships(AnalyticsSearchEventMessage event,
                                                                        Ownership rawOwnership) {
        List<SearchJourneyOwnership> ownerships = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        if (rawOwnership.resolved()) {
            appendSearchJourneyOwnership(
                    ownerships,
                    seenKeys,
                    SearchJourneyOwnership.of(rawOwnership.itemId(), rawOwnership.storeId(), rawOwnership.sellerId())
            );
        }

        List<Long> resultItemIds = event.getResultItemIds() == null ? List.of() : event.getResultItemIds();
        for (Long resultItemId : resultItemIds) {
            Ownership ownership = resolveOwnership(resultItemId, null, null);
            if (!ownership.resolved()) {
                continue;
            }
            appendSearchJourneyOwnership(
                    ownerships,
                    seenKeys,
                    SearchJourneyOwnership.of(ownership.itemId(), ownership.storeId(), ownership.sellerId())
            );
        }

        return List.copyOf(ownerships);
    }

    private void appendSearchJourneyOwnership(List<SearchJourneyOwnership> ownerships,
                                              Set<String> seenKeys,
                                              SearchJourneyOwnership ownership) {
        if (ownership == null || ownership.storeId() == null || ownership.sellerId() == null) {
            return;
        }
        String key = ownership.storeId() + ":" + ownership.sellerId();
        if (seenKeys.add(key)) {
            ownerships.add(ownership);
        }
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

    private String normalizeChannelType(String channelType) {
        String normalized = normalize(channelType);
        return normalized.isBlank() ? null : normalized;
    }

    private String resolveOrderDomainType(String channelType) {
        if (DOMAIN_FUNDING.equals(channelType)) {
            return DOMAIN_FUNDING;
        }
        if (DOMAIN_HOT_DEAL.equals(channelType)) {
            return DOMAIN_HOT_DEAL;
        }
        return DOMAIN_NORMAL;
    }

    private Long resolveCampaignId(String channelType, Long channelRefId) {
        return DOMAIN_FUNDING.equals(channelType) ? channelRefId : null;
    }

    private Long resolveHotDealId(String channelType, Long channelRefId) {
        return DOMAIN_HOT_DEAL.equals(channelType) ? channelRefId : null;
    }

    private String resolveJourneyId(
            String journeyId,
            String correlationId,
            String sessionId,
            Long userId,
            Long orderId,
            Long campaignId,
            Long hotDealId,
            Long purchaseId
    ) {
        if (journeyId != null && !journeyId.isBlank()) {
            return journeyId;
        }
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId;
        }
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId;
        }
        if (orderId != null) {
            return "order-" + orderId;
        }
        if (campaignId != null) {
            return "campaign-" + campaignId;
        }
        if (hotDealId != null) {
            return "hotdeal-" + hotDealId;
        }
        if (purchaseId != null) {
            return "purchase-" + purchaseId;
        }
        if (userId != null) {
            return "user-" + userId;
        }
        return null;
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

    private Long firstNonNull(Long first, Long second, Long third) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return third;
    }

    private String toJson(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return null;
        }
        return JsonUtils.toJson(properties);
    }

    private String buildSearchPropertiesJson(AnalyticsSearchEventMessage event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (event != null && event.getResultItemIds() != null && !event.getResultItemIds().isEmpty()) {
            properties.put("resultItemIds", event.getResultItemIds());
        }
        return toJson(properties);
    }

    private void putIfPresent(Map<String, Object> properties, String key, Object value) {
        if (value != null) {
            properties.put(key, value);
        }
    }

    private record Ownership(Long itemId, Long storeId, Long sellerId, Long referenceTotalAmount) {
        private boolean resolved() {
            return itemId != null && storeId != null && sellerId != null;
        }
    }

    private record SearchJourneyOwnership(Long itemId, Long storeId, Long sellerId) {
        private static SearchJourneyOwnership of(Long itemId, Long storeId, Long sellerId) {
            return new SearchJourneyOwnership(itemId, storeId, sellerId);
        }
    }
}
