package com.example.search.consumer.stock;

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
@InboxConsumerBinding(consumerName = SearchStockEventProcessor.CONSUMER_NAME)
public class SearchStockEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "search-stock-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "SEARCH_STOCK_EVENT";

    private final SearchIndexingService searchIndexingService;
    private final Map<String, EventSpec<StockEventMessage>> eventSpecs;

    public SearchStockEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            SearchIndexingService searchIndexingService
    ) {
        super(idempotentConsumerService);
        this.searchIndexingService = searchIndexingService;
        this.eventSpecs = Map.of(
                "ITEM_AVAILABLE_STOCK_CHANGED",
                EventSpec.of(StockEventMessage.class, this::hasItemId, this::handleAvailableStockChanged)
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected Map<String, EventSpec<StockEventMessage>> eventSpecs() {
        return eventSpecs;
    }

    @Override
    protected void onInvalidEnvelope(String eventId, String eventType, String message) {
        log.warn("[SearchStockEventProcessor] invalid envelope. eventId={}, eventType={}", eventId, eventType);
    }

    @Override
    protected <T extends com.example.event.consumer.EventEnvelope> void onInvalidPayload(
            T event,
            String message,
            String eventId,
            String eventType
    ) {
        log.warn("[SearchStockEventProcessor] invalid payload. eventId={}, eventType={}", eventId, eventType);
    }

    private boolean hasItemId(StockEventMessage event) {
        return event != null && event.getItemId() != null;
    }

    private void handleAvailableStockChanged(StockEventMessage event) {
        searchIndexingService.updateAvailableStock(event.getItemId(), event.getAvailableStockTotal());
    }
}
