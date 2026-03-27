package com.example.funding.consumer.payment;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.funding.service.command.FundingParticipationSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingPaymentEventProcessorTest {

    @Mock
    private FundingParticipationSyncService fundingParticipationSyncService;

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    private FundingPaymentEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new FundingPaymentEventProcessor(fundingParticipationSyncService, idempotentConsumerService);
    }

    @Test
    void process_paymentCompleted_routesToSyncService() {
        stubIdempotentExecution("evt-paid");

        String message = """
                {"eventId":"evt-paid","eventType":"PAYMENT_COMPLETED","paymentId":10,"orderId":101,"userId":1001}
                """;

        processor.process(message, "evt-paid", "PAYMENT_COMPLETED");

        verify(fundingParticipationSyncService).syncPaymentCompleted(any(PaymentEventMessage.class));
    }

    @Test
    void process_paymentRefunded_routesToSyncService() {
        stubIdempotentExecution("evt-refunded");

        String message = """
                {"eventId":"evt-refunded","eventType":"PAYMENT_REFUNDED","paymentId":11,"orderId":101,"userId":1001}
                """;

        processor.process(message, "evt-refunded", "PAYMENT_REFUNDED");

        verify(fundingParticipationSyncService).syncPaymentRefunded(any(PaymentEventMessage.class));
    }

    @Test
    void process_missingOrderId_skipsProcessing() {
        String message = """
                {"eventId":"evt-no-order","eventType":"PAYMENT_COMPLETED","paymentId":10,"userId":1001}
                """;

        processor.process(message, "evt-no-order", "PAYMENT_COMPLETED");

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(fundingParticipationSyncService);
    }

    @Test
    void process_unsupportedType_isIgnoredAfterIdempotentGate() {
        String message = """
                {"eventId":"evt-ignored","eventType":"PAYMENT_CANCELLED","paymentId":10,"orderId":101,"userId":1001}
                """;

        processor.process(message, "evt-ignored", "PAYMENT_CANCELLED");

        verifyNoInteractions(idempotentConsumerService);
        verify(fundingParticipationSyncService, never()).syncPaymentCompleted(any(PaymentEventMessage.class));
        verify(fundingParticipationSyncService, never()).syncPaymentRefunded(any(PaymentEventMessage.class));
    }

    private void stubIdempotentExecution(String eventId) {
        when(idempotentConsumerService.executeIdempotent(eq(eventId), eq("PAYMENT_EVENT"), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<Object> supplier = invocation.getArgument(2);
                    return Optional.ofNullable(supplier.get());
                });
    }
}
