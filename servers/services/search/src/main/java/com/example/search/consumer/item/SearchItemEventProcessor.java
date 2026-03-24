package com.example.search.consumer.item;

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
@InboxConsumerBinding(consumerName = SearchItemEventProcessor.CONSUMER_NAME)
public class SearchItemEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "search-item-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "SEARCH_ITEM_EVENT";

    private final SearchIndexingService searchIndexingService;
    private final Map<String, EventSpec<ItemEventMessage>> eventSpecs;

    public SearchItemEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            SearchIndexingService searchIndexingService
    ) {
        super(idempotentConsumerService);
        this.searchIndexingService = searchIndexingService;
        this.eventSpecs = Map.of(
                "ITEM_CREATED", EventSpec.of(ItemEventMessage.class, this::hasItemId, this::handleUpsert),
                "ITEM_UPDATED", EventSpec.of(ItemEventMessage.class, this::hasItemId, this::handleUpsert),
                "ITEM_STATUS_CHANGED", EventSpec.of(ItemEventMessage.class, this::hasItemId, this::handleUpsert),
                "ITEM_DELETED", EventSpec.of(ItemEventMessage.class, this::hasItemId, this::handleDelete)
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected Map<String, EventSpec<ItemEventMessage>> eventSpecs() {
        return eventSpecs;
    }

    @Override
    protected void onInvalidEnvelope(String eventId, String eventType, String message) {
        log.warn("[SearchItemEventProcessor] invalid envelope. eventId={}, eventType={}", eventId, eventType);
    }

    @Override
    protected <T extends com.example.event.consumer.EventEnvelope> void onInvalidPayload(
            T event,
            String message,
            String eventId,
            String eventType
    ) {
        log.warn("[SearchItemEventProcessor] invalid payload. eventId={}, eventType={}", eventId, eventType);
    }

    private boolean hasItemId(ItemEventMessage event) {
        return event != null && event.getItemId() != null;
    }

    private void handleUpsert(ItemEventMessage event) {
        searchIndexingService.upsertItem(event.getItemId());
        log.debug("Search item index upsert completed. itemId={}", event.getItemId());
    }

    private void handleDelete(ItemEventMessage event) {
        searchIndexingService.deleteItem(event.getItemId());
        log.debug("Search item index delete completed. itemId={}", event.getItemId());
    }
}
