package com.example.notification.consumer.hotdeal;

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
class NotificationHotDealEventConsumerTest {

    @Mock
    private InboxEnqueueService inboxEnqueueService;

    @Mock
    private NotificationHotDealEventProcessor notificationHotDealEventProcessor;

    private EventConsumerRoutingProperties routingProperties;
    private NotificationHotDealEventConsumer consumer;

    @BeforeEach
    void setUp() {
        routingProperties = new EventConsumerRoutingProperties();
        consumer = new NotificationHotDealEventConsumer(
                new InboxRoutingSupport(inboxEnqueueService, new EventConsumerRoutingResolver(routingProperties)),
                notificationHotDealEventProcessor
        );
    }

    @Test
    void consume_directMode_dispatchesToProcessor() {
        configureDirectMode();
        String message = """
                {"eventId":"evt-hotdeal-1","eventType":"HOT_DEAL_STARTED","hotDealId":11}
                """;
        when(notificationHotDealEventProcessor.supports("HOT_DEAL_STARTED")).thenReturn(true);

        consumer.consume(recordOf(message));

        verify(notificationHotDealEventProcessor).process(eq(message), eq("evt-hotdeal-1"), eq("HOT_DEAL_STARTED"));
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_inboxMode_enqueuesMessage() {
        String message = """
                {"eventId":"evt-hotdeal-2","eventType":"HOT_DEAL_STARTED","hotDealId":12}
                """;
        when(notificationHotDealEventProcessor.supports("HOT_DEAL_STARTED")).thenReturn(true);
        when(inboxEnqueueService.enqueue(
                NotificationHotDealEventProcessor.CONSUMER_NAME,
                "evt-hotdeal-2",
                "HOT_DEAL_STARTED",
                message
        )).thenReturn(true);

        consumer.consume(recordOf(message));

        verify(inboxEnqueueService).enqueue(
                NotificationHotDealEventProcessor.CONSUMER_NAME,
                "evt-hotdeal-2",
                "HOT_DEAL_STARTED",
                message
        );
        verify(notificationHotDealEventProcessor, never()).process(message, "evt-hotdeal-2", "HOT_DEAL_STARTED");
    }

    private ConsumerRecord<String, Object> recordOf(Object value) {
        return new ConsumerRecord<>("hotdeal-events", 0, 0L, null, value);
    }

    private void configureDirectMode() {
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.DIRECT);
        routingProperties.getRouting().put(NotificationHotDealEventProcessor.CONSUMER_NAME, routing);
    }
}
