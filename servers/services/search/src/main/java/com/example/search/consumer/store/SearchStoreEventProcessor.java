package com.example.search.consumer.store;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.search.service.index.SearchIndexingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = SearchStoreEventProcessor.CONSUMER_NAME)
public class SearchStoreEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "search-store-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "SEARCH_STORE_EVENT";

    private final SearchIndexingService searchIndexingService;
    private final Map<String, EventSpec<StoreEventMessage>> eventSpecs;

    public SearchStoreEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            SearchIndexingService searchIndexingService
    ) {
        super(idempotentConsumerService);
        this.searchIndexingService = searchIndexingService;
        this.eventSpecs = Map.of(
                "StoreUpdated", EventSpec.of(StoreEventMessage.class, this::hasStoreId, this::handleStoreUpdated)
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected Map<String, EventSpec<StoreEventMessage>> eventSpecs() {
        return eventSpecs;
    }

    @Override
    protected void onInvalidEnvelope(String eventId, String eventType, String message) {
        log.warn("[SearchStoreEventProcessor] invalid envelope. eventId={}, eventType={}", eventId, eventType);
    }

    @Override
    protected <T extends com.example.event.consumer.EventEnvelope> void onInvalidPayload(
            T event,
            String message,
            String eventId,
            String eventType
    ) {
        log.warn("[SearchStoreEventProcessor] invalid payload. eventId={}, eventType={}", eventId, eventType);
    }

    private boolean hasStoreId(StoreEventMessage event) {
        return event != null && event.getStoreId() != null;
    }

    private void handleStoreUpdated(StoreEventMessage event) {
        searchIndexingService.reindexStore(event.getStoreId());
    }
}
