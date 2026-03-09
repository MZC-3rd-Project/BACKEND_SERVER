package com.example.analyticsdashboard.consumer.item;

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
class AnalyticsItemEventConsumerTest {

    @Mock
    private InboxEnqueueService inboxEnqueueService;

    @Mock
    private AnalyticsItemEventProcessor analyticsItemEventProcessor;

    private EventConsumerRoutingProperties routingProperties;
    private AnalyticsItemEventConsumer consumer;

    @BeforeEach
    void setUp() {
        routingProperties = new EventConsumerRoutingProperties();
        consumer = new AnalyticsItemEventConsumer(
                new InboxRoutingSupport(inboxEnqueueService, new EventConsumerRoutingResolver(routingProperties)),
                analyticsItemEventProcessor
        );
    }

    @Test
    void consume_directMode_dispatchesToProcessor() {
        configureDirectMode();
        String message = """
                {
                  "eventId": "evt-item-1",
                  "eventType": "ITEM_CREATED",
                  "itemId": 101
                }
                """;
        when(analyticsItemEventProcessor.supports("ITEM_CREATED")).thenReturn(true);

        consumer.consume(recordOf(message));

        verify(analyticsItemEventProcessor).process(message, "evt-item-1", "ITEM_CREATED");
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_inboxMode_enqueuesMessage() {
        String message = """
                {
                  "eventId": "evt-item-2",
                  "eventType": "ITEM_CREATED",
                  "itemId": 101
                }
                """;
        when(analyticsItemEventProcessor.supports("ITEM_CREATED")).thenReturn(true);

        consumer.consume(recordOf(message));

        verify(inboxEnqueueService).enqueue(
                AnalyticsItemEventProcessor.CONSUMER_NAME,
                "evt-item-2",
                "ITEM_CREATED",
                message
        );
        verify(analyticsItemEventProcessor, never()).process(message, "evt-item-2", "ITEM_CREATED");
    }

    @Test
    void consume_skipsInvalidEnvelope() {
        String message = """
                {
                  "eventType": "ITEM_CREATED",
                  "itemId": 101
                }
                """;

        consumer.consume(recordOf(message));

        verifyNoInteractions(analyticsItemEventProcessor);
        verifyNoInteractions(inboxEnqueueService);
    }

    private ConsumerRecord<String, Object> recordOf(Object value) {
        return new ConsumerRecord<>("item-events", 0, 0L, null, value);
    }

    private void configureDirectMode() {
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.DIRECT);
        routingProperties.getRouting().put(AnalyticsItemEventProcessor.CONSUMER_NAME, routing);
    }
}
