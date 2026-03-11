package com.example.analyticsdashboard.consumer.sales;

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
class AnalyticsSalesEventConsumerTest {

    @Mock
    private InboxEnqueueService inboxEnqueueService;

    @Mock
    private AnalyticsSalesEventProcessor analyticsSalesEventProcessor;

    private EventConsumerRoutingProperties routingProperties;
    private AnalyticsSalesEventConsumer consumer;

    @BeforeEach
    void setUp() {
        routingProperties = new EventConsumerRoutingProperties();
        consumer = new AnalyticsSalesEventConsumer(
                new InboxRoutingSupport(inboxEnqueueService, new EventConsumerRoutingResolver(routingProperties)),
                analyticsSalesEventProcessor
        );
    }

    @Test
    void consume_directMode_dispatchesToProcessor() {
        configureDirectMode();
        String message = """
                {
                  "eventId": "evt-sales-1",
                  "eventType": "PURCHASE_CREATED",
                  "itemId": 101
                }
                """;
        when(analyticsSalesEventProcessor.supports("PURCHASE_CREATED")).thenReturn(true);

        consumer.consume(recordOf(message));

        verify(analyticsSalesEventProcessor).process(message, "evt-sales-1", "PURCHASE_CREATED");
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_inboxMode_enqueuesMessage() {
        String message = """
                {
                  "eventId": "evt-sales-2",
                  "eventType": "PURCHASE_CREATED",
                  "itemId": 101
                }
                """;
        when(analyticsSalesEventProcessor.supports("PURCHASE_CREATED")).thenReturn(true);

        consumer.consume(recordOf(message));

        verify(inboxEnqueueService).enqueue(
                AnalyticsSalesEventProcessor.CONSUMER_NAME,
                "evt-sales-2",
                "PURCHASE_CREATED",
                message
        );
        verify(analyticsSalesEventProcessor, never()).process(message, "evt-sales-2", "PURCHASE_CREATED");
    }

    @Test
    void consume_skipsInvalidEnvelope() {
        String message = """
                {
                  "eventType": "PURCHASE_CREATED",
                  "itemId": 101
                }
                """;

        consumer.consume(recordOf(message));

        verifyNoInteractions(analyticsSalesEventProcessor);
        verifyNoInteractions(inboxEnqueueService);
    }

    private ConsumerRecord<String, Object> recordOf(Object value) {
        return new ConsumerRecord<>("sales-events", 0, 0L, null, value);
    }

    private void configureDirectMode() {
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.DIRECT);
        routingProperties.getRouting().put(AnalyticsSalesEventProcessor.CONSUMER_NAME, routing);
    }
}
