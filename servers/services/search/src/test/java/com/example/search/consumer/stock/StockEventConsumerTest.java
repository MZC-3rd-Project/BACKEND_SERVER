package com.example.search.consumer.stock;

import com.example.event.consumer.ConsumerRoutingMode;
import com.example.event.consumer.EventConsumerRoutingProperties;
import com.example.event.consumer.EventConsumerRoutingResolver;
import com.example.event.inbox.InboxEnqueueService;
import com.example.event.inbox.InboxRoutingSupport;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockEventConsumerTest {

    @Mock
    private InboxEnqueueService inboxEnqueueService;

    @Mock
    private SearchStockEventProcessor searchStockEventProcessor;

    private EventConsumerRoutingProperties routingProperties;
    private StockEventConsumer stockEventConsumer;

    @BeforeEach
    void setUp() {
        routingProperties = new EventConsumerRoutingProperties();
        stockEventConsumer = new StockEventConsumer(
                new InboxRoutingSupport(inboxEnqueueService, new EventConsumerRoutingResolver(routingProperties)),
                searchStockEventProcessor
        );
    }

    @Test
    void consume_directMode_dispatchesToProcessor() {
        configureDirectMode();
        String message = """
                {"eventId":"evt-1","eventType":"STOCK_DECREASED","itemId":101}
                """;
        when(searchStockEventProcessor.supports("STOCK_DECREASED")).thenReturn(true);

        stockEventConsumer.consume(recordOf(message));

        verify(searchStockEventProcessor).process(message, "evt-1", "STOCK_DECREASED");
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_inboxMode_enqueuesMessage() {
        String message = """
                {"eventId":"evt-2","eventType":"ITEM_AVAILABLE_STOCK_CHANGED","itemId":101}
                """;
        when(searchStockEventProcessor.supports("ITEM_AVAILABLE_STOCK_CHANGED")).thenReturn(true);
        when(inboxEnqueueService.enqueue(
                SearchStockEventProcessor.CONSUMER_NAME,
                "evt-2",
                "ITEM_AVAILABLE_STOCK_CHANGED",
                message
        )).thenReturn(true);

        stockEventConsumer.consume(recordOf(message));

        verify(inboxEnqueueService).enqueue(
                SearchStockEventProcessor.CONSUMER_NAME,
                "evt-2",
                "ITEM_AVAILABLE_STOCK_CHANGED",
                message
        );
        verify(searchStockEventProcessor, never()).process(message, "evt-2", "ITEM_AVAILABLE_STOCK_CHANGED");
    }

    @Test
    void consume_skipsUnsupportedType() {
        configureDirectMode();
        String message = """
                {"eventId":"evt-3","eventType":"STOCK_UNKNOWN","itemId":101}
                """;
        when(searchStockEventProcessor.supports("STOCK_UNKNOWN")).thenReturn(false);

        stockEventConsumer.consume(recordOf(message));

        verify(searchStockEventProcessor).supports("STOCK_UNKNOWN");
        verify(searchStockEventProcessor, never()).process(message, "evt-3", "STOCK_UNKNOWN");
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_skipsInvalidEnvelope() {
        String message = """
                {"eventType":"STOCK_DECREASED","itemId":101}
                """;

        stockEventConsumer.consume(recordOf(message));

        verifyNoInteractions(searchStockEventProcessor);
        verifyNoInteractions(inboxEnqueueService);
    }

    private ConsumerRecord<String, Object> recordOf(Object value) {
        return new ConsumerRecord<>("stock-events", 0, 0L, null, value);
    }

    private void configureDirectMode() {
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.DIRECT);
        routingProperties.getRouting().put(SearchStockEventProcessor.CONSUMER_NAME, routing);
    }
}
