package com.example.analyticsdashboard.consumer.search;

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
class AnalyticsSearchEventConsumerTest {

    @Mock
    private InboxEnqueueService inboxEnqueueService;

    @Mock
    private AnalyticsSearchEventProcessor analyticsSearchEventProcessor;

    private EventConsumerRoutingProperties routingProperties;
    private AnalyticsSearchEventConsumer consumer;

    @BeforeEach
    void setUp() {
        routingProperties = new EventConsumerRoutingProperties();
        consumer = new AnalyticsSearchEventConsumer(
                new InboxRoutingSupport(inboxEnqueueService, new EventConsumerRoutingResolver(routingProperties)),
                analyticsSearchEventProcessor
        );
    }

    @Test
    void consume_directMode_dispatchesToProcessor() {
        configureDirectMode();
        String message = """
                {
                  "eventId": "evt-search-1",
                  "eventType": "SEARCH_EXECUTED"
                }
                """;
        when(analyticsSearchEventProcessor.supports("SEARCH_EXECUTED")).thenReturn(true);

        consumer.consume(recordOf(message));

        verify(analyticsSearchEventProcessor).process(message, "evt-search-1", "SEARCH_EXECUTED");
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_inboxMode_enqueuesMessage() {
        String message = """
                {
                  "eventId": "evt-search-2",
                  "eventType": "SEARCH_EXECUTED"
                }
                """;
        when(analyticsSearchEventProcessor.supports("SEARCH_EXECUTED")).thenReturn(true);

        consumer.consume(recordOf(message));

        verify(inboxEnqueueService).enqueue(
                AnalyticsSearchEventProcessor.CONSUMER_NAME,
                "evt-search-2",
                "SEARCH_EXECUTED",
                message
        );
        verify(analyticsSearchEventProcessor, never()).process(message, "evt-search-2", "SEARCH_EXECUTED");
    }

    @Test
    void consume_skipsInvalidEnvelope() {
        String message = """
                {
                  "eventType": "SEARCH_EXECUTED"
                }
                """;

        consumer.consume(recordOf(message));

        verifyNoInteractions(analyticsSearchEventProcessor);
        verifyNoInteractions(inboxEnqueueService);
    }

    private ConsumerRecord<String, Object> recordOf(Object value) {
        return new ConsumerRecord<>("search-events", 0, 0L, null, value);
    }

    private void configureDirectMode() {
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.DIRECT);
        routingProperties.getRouting().put(AnalyticsSearchEventProcessor.CONSUMER_NAME, routing);
    }
}
