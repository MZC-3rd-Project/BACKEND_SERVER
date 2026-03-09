package com.example.notification.consumer.funding;

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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationFundingEventConsumerTest {

    @Mock
    private InboxEnqueueService inboxEnqueueService;

    @Mock
    private NotificationFundingEventProcessor notificationFundingEventProcessor;

    private EventConsumerRoutingProperties routingProperties;
    private NotificationFundingEventConsumer consumer;

    @BeforeEach
    void setUp() {
        routingProperties = new EventConsumerRoutingProperties();
        consumer = new NotificationFundingEventConsumer(
                new InboxRoutingSupport(inboxEnqueueService, new EventConsumerRoutingResolver(routingProperties)),
                notificationFundingEventProcessor
        );
    }

    @Test
    void consume_directMode_dispatchesToProcessor() {
        configureDirectMode();
        String message = """
                {"eventId":"evt-funding-1","eventType":"FUNDING_SUCCEEDED","sellerId":3001}
                """;
        when(notificationFundingEventProcessor.supports("FUNDING_SUCCEEDED")).thenReturn(true);

        consumer.consume(recordOf(message));

        verify(notificationFundingEventProcessor).process(eq(message), eq("evt-funding-1"), eq("FUNDING_SUCCEEDED"));
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_inboxMode_enqueuesMessage() {
        String message = """
                {"eventId":"evt-funding-2","eventType":"FUNDING_FAILED","sellerId":3001}
                """;
        when(notificationFundingEventProcessor.supports("FUNDING_FAILED")).thenReturn(true);
        when(inboxEnqueueService.enqueue(
                NotificationFundingEventProcessor.CONSUMER_NAME,
                "evt-funding-2",
                "FUNDING_FAILED",
                message
        )).thenReturn(true);

        consumer.consume(recordOf(message));

        verify(inboxEnqueueService).enqueue(
                NotificationFundingEventProcessor.CONSUMER_NAME,
                "evt-funding-2",
                "FUNDING_FAILED",
                message
        );
        verify(notificationFundingEventProcessor, never()).process(message, "evt-funding-2", "FUNDING_FAILED");
    }

    private ConsumerRecord<String, Object> recordOf(Object value) {
        return new ConsumerRecord<>("funding-events", 0, 0L, null, value);
    }

    private void configureDirectMode() {
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.DIRECT);
        routingProperties.getRouting().put(NotificationFundingEventProcessor.CONSUMER_NAME, routing);
    }
}
