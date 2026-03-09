package com.example.hotdeal.consumer.stock;

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
    private StockEventProcessor stockEventProcessor;

    private EventConsumerRoutingProperties routingProperties;
    private StockEventConsumer consumer;

    @BeforeEach
    void setUp() {
        routingProperties = new EventConsumerRoutingProperties();
        consumer = new StockEventConsumer(
                new InboxRoutingSupport(inboxEnqueueService, new EventConsumerRoutingResolver(routingProperties)),
                stockEventProcessor
        );
    }

    @Test
    void consume_directMode_dispatchesToProcessor() {
        configureDirectMode();
        String message = """
                {
                  "eventId": "evt-stock-1",
                  "eventType": "STOCK_THRESHOLD_REACHED",
                  "itemId": 101,
                  "totalQuantity": 100,
                  "remainingQuantity": 50
                }
                """;
        when(stockEventProcessor.supports("STOCK_THRESHOLD_REACHED")).thenReturn(true);

        consumer.consume(recordOf(message));

        verify(stockEventProcessor).process(message, "evt-stock-1", "STOCK_THRESHOLD_REACHED");
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_inboxMode_enqueuesMessage() {
        String message = """
                {
                  "eventId": "evt-stock-2",
                  "eventType": "STOCK_THRESHOLD_REACHED",
                  "itemId": 101,
                  "totalQuantity": 100,
                  "remainingQuantity": 50
                }
                """;
        when(stockEventProcessor.supports("STOCK_THRESHOLD_REACHED")).thenReturn(true);

        consumer.consume(recordOf(message));

        verify(inboxEnqueueService).enqueue(
                StockEventProcessor.CONSUMER_NAME,
                "evt-stock-2",
                "STOCK_THRESHOLD_REACHED",
                message
        );
        verify(stockEventProcessor, never()).process(message, "evt-stock-2", "STOCK_THRESHOLD_REACHED");
    }

    @Test
    void consume_skipsInvalidEnvelope() {
        String message = """
                {
                  "eventType": "STOCK_THRESHOLD_REACHED",
                  "itemId": 101
                }
                """;

        consumer.consume(recordOf(message));

        verifyNoInteractions(stockEventProcessor);
        verifyNoInteractions(inboxEnqueueService);
    }

    private ConsumerRecord<String, Object> recordOf(Object value) {
        return new ConsumerRecord<>("stock-events", 0, 0L, null, value);
    }

    private void configureDirectMode() {
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.DIRECT);
        routingProperties.getRouting().put(StockEventProcessor.CONSUMER_NAME, routing);
    }
}
