package com.example.chat.consumer;

import com.example.chat.service.command.ChatFundingSyncService;
import com.example.config.kafka.IdempotentConsumerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingEventConsumerTest {

    @Mock
    private ChatFundingSyncService chatFundingSyncService;

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @InjectMocks
    private FundingEventConsumer fundingEventConsumer;

    @Test
    void consume_routesFundingCreatedToSyncService() {
        stubIdempotentExecution();

        String message = """
                {
                  "eventId": "evt-funding-1",
                  "eventType": "FUNDING_CREATED",
                  "campaignId": 100,
                  "itemId": 200,
                  "sellerId": 300
                }
                """;

        fundingEventConsumer.consume(message);

        verify(chatFundingSyncService).syncFundingCreated(any(FundingEventMessage.class));
    }

    @Test
    void consume_routesFundingClosedEventsToReadOnlySync() {
        stubIdempotentExecution();

        String message = """
                {
                  "eventId": "evt-funding-2",
                  "eventType": "FUNDING_FAILED",
                  "campaignId": 100,
                  "itemId": 200,
                  "sellerId": 300
                }
                """;

        fundingEventConsumer.consume(message);

        verify(chatFundingSyncService).syncFundingClosed(any(FundingEventMessage.class));
    }

    @Test
    void consume_ignoresInvalidEvent() {
        String message = """
                {
                  "eventType": "FUNDING_CREATED",
                  "campaignId": 100
                }
                """;

        fundingEventConsumer.consume(message);

        verify(idempotentConsumerService, never()).executeIdempotent(anyString(), anyString(), any());
        verify(chatFundingSyncService, never()).syncFundingCreated(any(FundingEventMessage.class));
    }

    private void stubIdempotentExecution() {
        when(idempotentConsumerService.executeIdempotent(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<Object> supplier = invocation.getArgument(2);
                    return Optional.ofNullable(supplier.get());
                });
    }
}
