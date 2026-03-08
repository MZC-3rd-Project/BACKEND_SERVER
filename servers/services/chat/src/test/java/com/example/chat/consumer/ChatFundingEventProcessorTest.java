package com.example.chat.consumer;

import com.example.chat.service.command.ChatFundingSyncService;
import com.example.config.kafka.IdempotentConsumerService;
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
class ChatFundingEventProcessorTest {

    @Mock
    private ChatFundingSyncService chatFundingSyncService;

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    private ChatFundingEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ChatFundingEventProcessor(chatFundingSyncService, idempotentConsumerService);
    }

    @Test
    void process_fundingCreated_routesToSyncService() {
        stubIdempotentExecution("evt-funding-1");

        String message = """
                {"eventId":"evt-funding-1","eventType":"FUNDING_CREATED","campaignId":100,"itemId":200,"sellerId":300}
                """;

        processor.process(message, "evt-funding-1", "FUNDING_CREATED");

        verify(chatFundingSyncService).syncFundingCreated(any(FundingEventMessage.class));
    }

    @Test
    void process_fundingClosed_routesToReadOnlySync() {
        stubIdempotentExecution("evt-funding-2");

        String message = """
                {"eventId":"evt-funding-2","eventType":"FUNDING_FAILED","campaignId":100,"itemId":200,"sellerId":300}
                """;

        processor.process(message, "evt-funding-2", "FUNDING_FAILED");

        verify(chatFundingSyncService).syncFundingClosed(any(FundingEventMessage.class));
    }

    @Test
    void process_invalidEnvelope_skipsProcessing() {
        String message = """
                {"eventType":"FUNDING_CREATED","campaignId":100}
                """;

        processor.process(message, null, "FUNDING_CREATED");

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(chatFundingSyncService);
    }

    @Test
    void process_ignoresUnsupportedTypeAfterIdempotentGate() {
        stubIdempotentExecution("evt-funding-3");

        String message = """
                {"eventId":"evt-funding-3","eventType":"FUNDING_UNKNOWN","campaignId":100}
                """;

        processor.process(message, "evt-funding-3", "FUNDING_UNKNOWN");

        verify(idempotentConsumerService).executeIdempotent(eq("evt-funding-3"), eq("FUNDING_EVENT"), any());
        verify(chatFundingSyncService, never()).syncFundingCreated(any(FundingEventMessage.class));
        verify(chatFundingSyncService, never()).syncFundingParticipated(any(FundingEventMessage.class));
        verify(chatFundingSyncService, never()).syncFundingRefunded(any(FundingEventMessage.class));
        verify(chatFundingSyncService, never()).syncFundingClosed(any(FundingEventMessage.class));
    }

    private void stubIdempotentExecution(String eventId) {
        when(idempotentConsumerService.executeIdempotent(eq(eventId), eq("FUNDING_EVENT"), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<Object> supplier = invocation.getArgument(2);
                    return Optional.ofNullable(supplier.get());
                });
    }
}
