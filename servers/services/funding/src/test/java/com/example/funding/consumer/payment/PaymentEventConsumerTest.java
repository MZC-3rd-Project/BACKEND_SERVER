package com.example.funding.consumer.payment;

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
    private FundingPaymentEventProcessor fundingPaymentEventProcessor;

    private EventConsumerRoutingProperties routingProperties;
    private PaymentEventConsumer paymentEventConsumer;

    @BeforeEach
    void setUp() {
        routingProperties = new EventConsumerRoutingProperties();
        paymentEventConsumer = new PaymentEventConsumer(
                new InboxRoutingSupport(inboxEnqueueService, new EventConsumerRoutingResolver(routingProperties)),
                fundingPaymentEventProcessor
        );
    }

    @Test
    void consume_directMode_dispatchesToProcessor() {
        configureDirectMode();
        String message = """
                {"eventId":"evt-1","eventType":"PAYMENT_COMPLETED","orderId":101,"paymentId":22,"userId":33}
                """;
        when(fundingPaymentEventProcessor.supports("PAYMENT_COMPLETED")).thenReturn(true);

        paymentEventConsumer.consume(recordOf(message));

        verify(fundingPaymentEventProcessor).process(message, "evt-1", "PAYMENT_COMPLETED");
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_invalidEnvelope_skipsProcessing() {
        configureDirectMode();
        String message = """
                {"eventType":"PAYMENT_COMPLETED","participationId":11,"paymentId":22,"userId":33}
                """;

        paymentEventConsumer.consume(recordOf(message));

        verifyNoInteractions(inboxEnqueueService);
        verifyNoInteractions(fundingPaymentEventProcessor);
    }

    @Test
    void consume_inboxMode_enqueuesMessage() {
        String message = """
                {"eventId":"evt-2","eventType":"PAYMENT_CANCELLED","orderId":101,"paymentId":22,"userId":33}
                """;
        when(fundingPaymentEventProcessor.supports("PAYMENT_CANCELLED")).thenReturn(true);
        when(inboxEnqueueService.enqueue(
                FundingPaymentEventProcessor.CONSUMER_NAME,
                "evt-2",
                "PAYMENT_CANCELLED",
                message
        )).thenReturn(true);

        paymentEventConsumer.consume(recordOf(message));

        verify(inboxEnqueueService).enqueue(
                FundingPaymentEventProcessor.CONSUMER_NAME,
                "evt-2",
                "PAYMENT_CANCELLED",
                message
        );
        verify(fundingPaymentEventProcessor, never()).process(message, "evt-2", "PAYMENT_CANCELLED");
    }

    private ConsumerRecord<String, Object> recordOf(Object value) {
        return new ConsumerRecord<>("payment-events", 0, 0L, null, value);
    }

    private void configureDirectMode() {
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.DIRECT);
        routingProperties.getRouting().put(FundingPaymentEventProcessor.CONSUMER_NAME, routing);
    }
}
