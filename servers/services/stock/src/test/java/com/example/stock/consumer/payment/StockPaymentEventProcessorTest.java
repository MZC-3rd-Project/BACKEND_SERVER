package com.example.stock.consumer.payment;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.stock.service.command.StockCommandService;
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
class StockPaymentEventProcessorTest {

    @Mock
    private StockCommandService stockCommandService;

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    private StockPaymentEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new StockPaymentEventProcessor(stockCommandService, idempotentConsumerService);
    }

    @Test
    void process_paymentCompleted_confirmsReservationsByOrderId() {
        String message = """
                {"eventId":"evt-1","eventType":"PAYMENT_COMPLETED","orderId":101,"userId":33}
                """;
        when(idempotentConsumerService.executeIdempotent(eq("evt-1"), eq("PAYMENT_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    supplier.get();
                    return Optional.empty();
                });

        processor.process(message, "evt-1", "PAYMENT_COMPLETED");

        verify(stockCommandService).confirmReservationsByOrderId(101L);
    }

    @Test
    void process_missingOrderIdAndReservationId_skipsProcessing() {
        String message = """
                {"eventId":"evt-2","eventType":"PAYMENT_COMPLETED","userId":33}
                """;

        processor.process(message, "evt-2", "PAYMENT_COMPLETED");

        verifyNoInteractions(idempotentConsumerService);
        verify(stockCommandService, never()).confirmReservationById(any());
        verify(stockCommandService, never()).confirmReservationsByOrderId(any());
        verify(stockCommandService, never()).cancelReservation(any());
        verify(stockCommandService, never()).cancelReservationsByOrderId(any());
    }

    @Test
    void process_invalidEnvelope_skipsProcessing() {
        String message = """
                {"eventType":"PAYMENT_COMPLETED","orderId":101,"userId":33}
                """;

        processor.process(message, null, "PAYMENT_COMPLETED");

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(stockCommandService);
    }

    @Test
    void process_whenOrderIdMissing_fallsBackToReservationId() {
        String message = """
                {"eventId":"evt-3","eventType":"PAYMENT_CANCELLED","reservationId":11,"userId":33}
                """;
        when(idempotentConsumerService.executeIdempotent(eq("evt-3"), eq("PAYMENT_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    supplier.get();
                    return Optional.empty();
                });

        processor.process(message, "evt-3", "PAYMENT_CANCELLED");

        verify(stockCommandService).cancelReservation(11L);
    }
}
