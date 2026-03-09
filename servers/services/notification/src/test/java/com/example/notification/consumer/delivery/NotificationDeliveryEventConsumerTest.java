package com.example.notification.consumer.delivery;

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
class NotificationDeliveryEventConsumerTest {

    @Mock
    private InboxEnqueueService inboxEnqueueService;

    @Mock
    private NotificationDeliveryEventProcessor notificationDeliveryEventProcessor;

    private EventConsumerRoutingProperties routingProperties;
    private NotificationDeliveryEventConsumer consumer;

    @BeforeEach
    void setUp() {
        routingProperties = new EventConsumerRoutingProperties();
        consumer = new NotificationDeliveryEventConsumer(
                new InboxRoutingSupport(inboxEnqueueService, new EventConsumerRoutingResolver(routingProperties)),
                notificationDeliveryEventProcessor
        );
    }

    @Test
    void consume_directMode_dispatchesToProcessor() {
        configureDirectMode();
        String message = """
                {
                  "eventId": "delivery-evt-1",
                  "eventType": "NOTIFICATION_DELIVERY_REQUESTED",
                  "notificationId": 101,
                  "channel": "IN_APP"
                }
                """;
        when(notificationDeliveryEventProcessor.supports("NOTIFICATION_DELIVERY_REQUESTED")).thenReturn(true);

        consumer.consume(recordOf(message));

        verify(notificationDeliveryEventProcessor).process(
                eq(message),
                eq("delivery-evt-1"),
                eq("NOTIFICATION_DELIVERY_REQUESTED")
        );
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_inboxMode_enqueuesMessage() {
        String message = """
                {
                  "eventId": "delivery-evt-2",
                  "eventType": "NOTIFICATION_DELIVERY_REQUESTED",
                  "notificationId": 202,
                  "channel": "EMAIL"
                }
                """;
        when(notificationDeliveryEventProcessor.supports("NOTIFICATION_DELIVERY_REQUESTED")).thenReturn(true);
        when(inboxEnqueueService.enqueue(
                NotificationDeliveryEventProcessor.CONSUMER_NAME,
                "delivery-evt-2",
                "NOTIFICATION_DELIVERY_REQUESTED",
                message
        )).thenReturn(true);

        consumer.consume(recordOf(message));

        verify(inboxEnqueueService).enqueue(
                NotificationDeliveryEventProcessor.CONSUMER_NAME,
                "delivery-evt-2",
                "NOTIFICATION_DELIVERY_REQUESTED",
                message
        );
        verify(notificationDeliveryEventProcessor, never()).process(
                message,
                "delivery-evt-2",
                "NOTIFICATION_DELIVERY_REQUESTED"
        );
    }

    @Test
    void consume_skipsUnsupportedType() {
        configureDirectMode();
        String message = """
                {
                  "eventId": "delivery-evt-3",
                  "eventType": "NOTIFICATION_DELIVERY_FAILED"
                }
                """;
        when(notificationDeliveryEventProcessor.supports("NOTIFICATION_DELIVERY_FAILED")).thenReturn(false);

        consumer.consume(recordOf(message));

        verify(notificationDeliveryEventProcessor).supports("NOTIFICATION_DELIVERY_FAILED");
        verify(notificationDeliveryEventProcessor, never()).process(
                message,
                "delivery-evt-3",
                "NOTIFICATION_DELIVERY_FAILED"
        );
        verifyNoInteractions(inboxEnqueueService);
    }

    private ConsumerRecord<String, Object> recordOf(Object value) {
        return new ConsumerRecord<>("notification-delivery-events", 0, 0L, null, value);
    }

    private void configureDirectMode() {
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.DIRECT);
        routingProperties.getRouting().put(NotificationDeliveryEventProcessor.CONSUMER_NAME, routing);
    }
}
