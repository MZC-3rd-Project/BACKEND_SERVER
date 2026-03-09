package com.example.search.consumer.funding;

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
@InboxConsumerBinding(consumerName = SearchFundingEventProcessor.CONSUMER_NAME)
public class SearchFundingEventProcessor extends AbstractSearchEventSpecProcessor<FundingEventMessage> {

    public static final String CONSUMER_NAME = "search-funding-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "FUNDING_EVENT";
    private static final String FUNDING_CREATED_EVENT_TYPE = "FUNDING_CREATED";
    private static final String FUNDING_SUCCEEDED_EVENT_TYPE = "FUNDING_SUCCEEDED";
    private static final String FUNDING_FAILED_EVENT_TYPE = "FUNDING_FAILED";
    private static final String FUNDING_CANCELLED_EVENT_TYPE = "FUNDING_CANCELLED";

    private final SearchIndexingService searchIndexingService;
    private final SearchResultCacheService searchResultCacheService;
    private final SearchIndexingFailureService searchIndexingFailureService;
    private final Map<String, EventSpec<? extends EventEnvelope>> eventSpecs;

    public SearchFundingEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            SearchIndexingService searchIndexingService,
            SearchResultCacheService searchResultCacheService,
            SearchIndexingFailureService searchIndexingFailureService,
            SearchMetricsService searchMetricsService
    ) {
        super(idempotentConsumerService, searchMetricsService, FundingEventMessage.class);
        this.searchIndexingService = searchIndexingService;
        this.searchResultCacheService = searchResultCacheService;
        this.searchIndexingFailureService = searchIndexingFailureService;
        this.eventSpecs = Map.of(
                FUNDING_CREATED_EVENT_TYPE, eventSpec(this::hasItemId, this::handleFundingCreated),
                FUNDING_SUCCEEDED_EVENT_TYPE, eventSpec(this::hasItemId, this::handleFundingSucceeded),
                FUNDING_FAILED_EVENT_TYPE, eventSpec(this::hasItemId, this::handleFundingFailed),
                FUNDING_CANCELLED_EVENT_TYPE, eventSpec(this::hasItemId, this::handleFundingCancelled)
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected void recordFailure(FundingEventMessage event, String message, Exception exception) {
        searchIndexingFailureService.recordFundingEventFailure(event, message, exception);
    }

    @Override
    protected Map<String, EventSpec<? extends EventEnvelope>> eventSpecs() {
        return eventSpecs;
    }

    private void handleFundingCreated(FundingEventMessage event) {
        searchIndexingService.applyFundingCreated(event.getItemId(), event.getCampaignId());
        searchResultCacheService.evictAll();
    }

    private void handleFundingSucceeded(FundingEventMessage event) {
        searchIndexingService.applyFundingClosed(event.getItemId(), event.getCampaignId(), "FUNDED");
        searchResultCacheService.evictAll();
    }

    private void handleFundingFailed(FundingEventMessage event) {
        searchIndexingService.applyFundingClosed(event.getItemId(), event.getCampaignId(), "FUND_FAILED");
        searchResultCacheService.evictAll();
    }

    private void handleFundingCancelled(FundingEventMessage event) {
        searchIndexingService.applyFundingClosed(event.getItemId(), event.getCampaignId(), "CLOSED");
        searchResultCacheService.evictAll();
    }

    @Override
    protected <E extends EventEnvelope> void onInvalidPayload(E event, String message, String eventId, String eventType) {
        log.error("[SearchFundingConsumer] eventId/eventType/itemId 누락. message={}", message);
    }

    private boolean hasItemId(FundingEventMessage event) {
        return event != null && event.getItemId() != null;
    }
}
