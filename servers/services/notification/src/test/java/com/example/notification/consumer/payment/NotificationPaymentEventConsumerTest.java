package com.example.notification.consumer.payment;

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
class NotificationPaymentEventConsumerTest {

    @Mock
    private InboxEnqueueService inboxEnqueueService;

    @Mock
    private NotificationPaymentEventProcessor notificationPaymentEventProcessor;

    private EventConsumerRoutingProperties routingProperties;
    private NotificationPaymentEventConsumer consumer;

    @BeforeEach
    void setUp() {
        routingProperties = new EventConsumerRoutingProperties();
        consumer = new NotificationPaymentEventConsumer(
                new InboxRoutingSupport(inboxEnqueueService, new EventConsumerRoutingResolver(routingProperties)),
                notificationPaymentEventProcessor
        );
    }

    @Test
    void consume_directMode_dispatchesToProcessor() {
        configureDirectMode();
        String message = """
                {"eventId":"evt-payment-1","eventType":"PAYMENT_COMPLETED","userId":101}
                """;
        when(notificationPaymentEventProcessor.supports("PAYMENT_COMPLETED")).thenReturn(true);

        consumer.consume(recordOf(message));

        verify(notificationPaymentEventProcessor).process(eq(message), eq("evt-payment-1"), eq("PAYMENT_COMPLETED"));
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_inboxMode_enqueuesMessage() {
        String message = """
                {"eventId":"evt-payment-2","eventType":"PAYMENT_COMPLETED","userId":101}
                """;
        when(notificationPaymentEventProcessor.supports("PAYMENT_COMPLETED")).thenReturn(true);
        when(inboxEnqueueService.enqueue(
                NotificationPaymentEventProcessor.CONSUMER_NAME,
                "evt-payment-2",
                "PAYMENT_COMPLETED",
                message
        )).thenReturn(true);

        consumer.consume(recordOf(message));

        verify(inboxEnqueueService).enqueue(
                NotificationPaymentEventProcessor.CONSUMER_NAME,
                "evt-payment-2",
                "PAYMENT_COMPLETED",
                message
        );
        verify(notificationPaymentEventProcessor, never()).process(message, "evt-payment-2", "PAYMENT_COMPLETED");
    }

    private ConsumerRecord<String, Object> recordOf(Object value) {
        return new ConsumerRecord<>("payment-events", 0, 0L, null, value);
    }

    private void configureDirectMode() {
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.DIRECT);
        routingProperties.getRouting().put(NotificationPaymentEventProcessor.CONSUMER_NAME, routing);
    }
}
