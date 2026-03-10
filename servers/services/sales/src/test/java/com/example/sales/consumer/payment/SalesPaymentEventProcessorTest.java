package com.example.sales.consumer.payment;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.sales.entity.Purchase;
import com.example.sales.repository.PurchaseRepository;
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
class SalesPaymentEventProcessorTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private Purchase purchase;

    private SalesPaymentEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SalesPaymentEventProcessor(purchaseRepository, idempotentConsumerService);
    }

    @Test
    void process_paymentCancelled_cancelsPurchaseByOrderId() {
        String message = """
                {"eventId":"evt-1","eventType":"PAYMENT_CANCELLED","orderId":101,"paymentId":22,"userId":33}
                """;
        when(idempotentConsumerService.executeIdempotent(eq("evt-1"), eq("PAYMENT_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    supplier.get();
                    return Optional.empty();
                });
        when(purchaseRepository.findByOrderId(101L)).thenReturn(Optional.of(purchase));

        processor.process(message, "evt-1", "PAYMENT_CANCELLED");

        verify(purchase).cancel();
    }

    @Test
    void process_invalidEnvelope_skipsProcessing() {
        String message = """
                {"eventType":"PAYMENT_COMPLETED","orderId":101,"paymentId":22,"userId":33}
                """;

        processor.process(message, null, "PAYMENT_COMPLETED");

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(purchaseRepository);
    }

    @Test
    void process_missingOrderIdAndPurchaseId_skipsProcessing() {
        String message = """
                {"eventId":"evt-2","eventType":"PAYMENT_COMPLETED","paymentId":22,"userId":33}
                """;

        processor.process(message, "evt-2", "PAYMENT_COMPLETED");

        verifyNoInteractions(idempotentConsumerService);
        verify(purchaseRepository, never()).findById(any());
        verify(purchaseRepository, never()).findByOrderId(any());
    }

    @Test
    void process_whenOrderIdMissing_fallsBackToPurchaseId() {
        String message = """
                {"eventId":"evt-3","eventType":"PAYMENT_COMPLETED","purchaseId":11,"paymentId":22,"userId":33}
                """;
        when(idempotentConsumerService.executeIdempotent(eq("evt-3"), eq("PAYMENT_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    supplier.get();
                    return Optional.empty();
                });
        when(purchaseRepository.findById(11L)).thenReturn(Optional.of(purchase));

        processor.process(message, "evt-3", "PAYMENT_COMPLETED");

        verify(purchase).confirm(22L);
    }
}
