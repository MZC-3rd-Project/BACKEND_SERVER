package com.example.funding.consumer.payment;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.funding.entity.FundingParticipation;
import com.example.funding.repository.FundingParticipationRepository;
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
    private FundingParticipationRepository participationRepository;

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private FundingParticipation participation;

    private FundingPaymentEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new FundingPaymentEventProcessor(participationRepository, idempotentConsumerService);
    }

    @Test
    void process_paymentCompleted_confirmsParticipation() {
        String message = """
                {"eventId":"evt-1","eventType":"PAYMENT_COMPLETED","participationId":11,"paymentId":22,"userId":33}
                """;
        when(idempotentConsumerService.executeIdempotent(eq("evt-1"), eq("PAYMENT_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    supplier.get();
                    return Optional.empty();
                });
        when(participationRepository.findById(11L)).thenReturn(Optional.of(participation));

        processor.process(message, "evt-1", "PAYMENT_COMPLETED");

        verify(participation).confirm(22L);
    }

    @Test
    void process_invalidEnvelope_skipsProcessing() {
        String message = """
                {"eventType":"PAYMENT_COMPLETED","participationId":11,"paymentId":22,"userId":33}
                """;

        processor.process(message, null, "PAYMENT_COMPLETED");

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(participationRepository);
    }

    @Test
    void process_missingParticipationId_skipsProcessing() {
        String message = """
                {"eventId":"evt-2","eventType":"PAYMENT_CANCELLED","paymentId":22,"userId":33}
                """;

        processor.process(message, "evt-2", "PAYMENT_CANCELLED");

        verifyNoInteractions(idempotentConsumerService);
        verify(participationRepository, never()).findById(any());
    }
}
