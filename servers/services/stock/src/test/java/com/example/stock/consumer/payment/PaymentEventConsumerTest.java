package com.example.stock.consumer.payment;

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
class PaymentEventConsumerTest {

    @Mock
    private InboxEnqueueService inboxEnqueueService;

    @Mock
    private StockPaymentEventProcessor stockPaymentEventProcessor;

    private EventConsumerRoutingProperties routingProperties;
    private PaymentEventConsumer paymentEventConsumer;

    @BeforeEach
    void setUp() {
        routingProperties = new EventConsumerRoutingProperties();
        paymentEventConsumer = new PaymentEventConsumer(
                new InboxRoutingSupport(inboxEnqueueService, new EventConsumerRoutingResolver(routingProperties)),
                stockPaymentEventProcessor
        );
    }

    @Test
    void consume_directMode_dispatchesToProcessor() {
        configureDirectMode();
        String message = """
                {"eventId":"evt-1","eventType":"PAYMENT_COMPLETED","orderId":101,"userId":33}
                """;
        when(stockPaymentEventProcessor.supports("PAYMENT_COMPLETED")).thenReturn(true);

        paymentEventConsumer.consume(recordOf(message));

        verify(stockPaymentEventProcessor).process(message, "evt-1", "PAYMENT_COMPLETED");
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_missingReservationId_skipsProcessing() {
        configureDirectMode();
        String message = """
                {"eventId":"evt-1","eventType":"PAYMENT_COMPLETED","userId":33}
                """;
        when(stockPaymentEventProcessor.supports("PAYMENT_COMPLETED")).thenReturn(true);

        paymentEventConsumer.consume(recordOf(message));

        verifyNoInteractions(inboxEnqueueService);
        verify(stockPaymentEventProcessor).supports("PAYMENT_COMPLETED");
        verify(stockPaymentEventProcessor).process(message, "evt-1", "PAYMENT_COMPLETED");
    }

    @Test
    void consume_inboxMode_enqueuesMessage() {
        String message = """
                {"eventId":"evt-2","eventType":"PAYMENT_TIMED_OUT","orderId":101,"userId":33}
                """;
        when(stockPaymentEventProcessor.supports("PAYMENT_TIMED_OUT")).thenReturn(true);
        when(inboxEnqueueService.enqueue(
                StockPaymentEventProcessor.CONSUMER_NAME,
                "evt-2",
                "PAYMENT_TIMED_OUT",
                message
        )).thenReturn(true);

        paymentEventConsumer.consume(recordOf(message));

        verify(inboxEnqueueService).enqueue(
                StockPaymentEventProcessor.CONSUMER_NAME,
                "evt-2",
                "PAYMENT_TIMED_OUT",
                message
        );
        verify(stockPaymentEventProcessor, never()).process(message, "evt-2", "PAYMENT_TIMED_OUT");
    }

    private ConsumerRecord<String, Object> recordOf(Object value) {
        return new ConsumerRecord<>("payment-events", 0, 0L, null, value);
    }

    private void configureDirectMode() {
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.DIRECT);
        routingProperties.getRouting().put(StockPaymentEventProcessor.CONSUMER_NAME, routing);
    }
}
