package com.example.search.consumer.hotdeal;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.search.consumer.support.AbstractSearchEventSpecProcessor;
import com.example.search.service.index.SearchIndexingFailureService;
import com.example.search.service.index.SearchIndexingService;
import com.example.search.service.metrics.SearchMetricsService;
import com.example.search.service.query.cache.SearchResultCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = SearchHotDealEventProcessor.CONSUMER_NAME)
public class SearchHotDealEventProcessor extends AbstractSearchEventSpecProcessor<HotDealEventMessage> {

    public static final String CONSUMER_NAME = "search-hotdeal-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "HOTDEAL_EVENT";
    private static final String HOT_DEAL_STARTED_EVENT_TYPE = "HOT_DEAL_STARTED";
    private static final String HOT_DEAL_ENDED_EVENT_TYPE = "HOT_DEAL_ENDED";
    private static final String HOT_DEAL_CANCELLED_EVENT_TYPE = "HOT_DEAL_CANCELLED";

    private final SearchIndexingService searchIndexingService;
    private final SearchResultCacheService searchResultCacheService;
    private final SearchIndexingFailureService searchIndexingFailureService;
    private final Map<String, EventSpec<? extends EventEnvelope>> eventSpecs;

    public SearchHotDealEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            SearchIndexingService searchIndexingService,
            SearchResultCacheService searchResultCacheService,
            SearchIndexingFailureService searchIndexingFailureService,
            SearchMetricsService searchMetricsService
    ) {
        super(idempotentConsumerService, searchMetricsService, HotDealEventMessage.class);
        this.searchIndexingService = searchIndexingService;
        this.searchResultCacheService = searchResultCacheService;
        this.searchIndexingFailureService = searchIndexingFailureService;
        this.eventSpecs = Map.of(
                HOT_DEAL_STARTED_EVENT_TYPE, eventSpec(this::hasItemId, this::handleHotDealStarted),
                HOT_DEAL_ENDED_EVENT_TYPE, eventSpec(this::hasItemId, this::handleHotDealEnded),
                HOT_DEAL_CANCELLED_EVENT_TYPE, eventSpec(this::hasItemId, this::handleHotDealCancelled)
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected void recordFailure(HotDealEventMessage event, String message, Exception exception) {
        searchIndexingFailureService.recordHotDealEventFailure(event, message, exception);
    }

    @Override
    protected Map<String, EventSpec<? extends EventEnvelope>> eventSpecs() {
        return eventSpecs;
    }

    private void handleHotDealStarted(HotDealEventMessage event) {
        searchIndexingService.applyHotDealStarted(event.getItemId(), event.getHotDealId(), event.getDiscountedPrice());
        searchResultCacheService.evictAll();
    }

    private void handleHotDealEnded(HotDealEventMessage event) {
        searchIndexingService.applyHotDealEnded(event.getItemId(), event.getHotDealId());
        searchResultCacheService.evictAll();
    }

    private void handleHotDealCancelled(HotDealEventMessage event) {
        searchIndexingService.applyHotDealEnded(event.getItemId(), event.getHotDealId());
        searchResultCacheService.evictAll();
    }

    @Override
    protected <E extends EventEnvelope> void onInvalidPayload(E event, String message, String eventId, String eventType) {
        log.error("[SearchHotDealConsumer] eventId/eventType/itemId 누락. message={}", message);
    }

    private boolean hasItemId(HotDealEventMessage event) {
        return event != null && event.getItemId() != null;
    }
}
