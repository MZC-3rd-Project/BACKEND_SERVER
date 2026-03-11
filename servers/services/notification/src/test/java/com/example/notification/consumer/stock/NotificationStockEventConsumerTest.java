package com.example.notification.consumer.stock;

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
class NotificationStockEventConsumerTest {

    @Mock
    private InboxEnqueueService inboxEnqueueService;

    @Mock
    private NotificationStockEventProcessor notificationStockEventProcessor;

    private EventConsumerRoutingProperties routingProperties;
    private NotificationStockEventConsumer consumer;

    @BeforeEach
    void setUp() {
        routingProperties = new EventConsumerRoutingProperties();
        consumer = new NotificationStockEventConsumer(
                new InboxRoutingSupport(inboxEnqueueService, new EventConsumerRoutingResolver(routingProperties)),
                notificationStockEventProcessor
        );
    }

    @Test
    void consume_directMode_dispatchesToProcessor() {
        configureDirectMode();
        String message = """
                {"eventId":"evt-stock-1","eventType":"STOCK_DEPLETED","itemId":44}
                """;
        when(notificationStockEventProcessor.supports("STOCK_DEPLETED")).thenReturn(true);

        consumer.consume(recordOf(message));

        verify(notificationStockEventProcessor).process(eq(message), eq("evt-stock-1"), eq("STOCK_DEPLETED"));
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_inboxMode_enqueuesMessage() {
        String message = """
                {"eventId":"evt-stock-2","eventType":"STOCK_DEPLETED","itemId":45}
                """;
        when(notificationStockEventProcessor.supports("STOCK_DEPLETED")).thenReturn(true);
        when(inboxEnqueueService.enqueue(
                NotificationStockEventProcessor.CONSUMER_NAME,
                "evt-stock-2",
                "STOCK_DEPLETED",
                message
        )).thenReturn(true);

        consumer.consume(recordOf(message));

        verify(inboxEnqueueService).enqueue(
                NotificationStockEventProcessor.CONSUMER_NAME,
                "evt-stock-2",
                "STOCK_DEPLETED",
                message
        );
        verify(notificationStockEventProcessor, never()).process(message, "evt-stock-2", "STOCK_DEPLETED");
    }

    private ConsumerRecord<String, Object> recordOf(Object value) {
        return new ConsumerRecord<>("stock-events", 0, 0L, null, value);
    }

    private void configureDirectMode() {
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.DIRECT);
        routingProperties.getRouting().put(NotificationStockEventProcessor.CONSUMER_NAME, routing);
    }
}
