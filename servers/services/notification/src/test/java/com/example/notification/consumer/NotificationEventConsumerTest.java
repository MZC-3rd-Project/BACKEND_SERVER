package com.example.notification.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.notification.client.ProductClient;
import com.example.notification.dto.command.request.CreateNotificationRequest;
import com.example.notification.service.command.NotificationCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private NotificationCommandService notificationCommandService;

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private NotificationEventConsumer notificationEventConsumer;

    @BeforeEach
    void setUp() {
        when(idempotentConsumerService.executeIdempotent(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<Object> supplier = invocation.getArgument(2);
                    return Optional.ofNullable(supplier.get());
                });
    }

    @Test
    void consumeFunding_dispatchesFundingSuccessNotification() {
        String message = """
                {
                  "eventId": "evt-funding-1",
                  "eventType": "FUNDING_SUCCEEDED",
                  "campaignId": 1001,
                  "itemId": 2001,
                  "sellerId": 3001,
                  "goalAmount": 100000,
                  "currentAmount": 120000,
                  "currentQuantity": 55
                }
                """;

        notificationEventConsumer.consumeFunding(message);

        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationCommandService).createAndSend(captor.capture(), eq(null));

        CreateNotificationRequest request = captor.getValue();
        assertThat(request.getRecipientId()).isEqualTo(3001L);
        assertThat(request.getType()).isEqualTo("FUNDING_SUCCESS");
        assertThat(request.getReferenceType()).isEqualTo("CAMPAIGN");
        assertThat(request.getReferenceId()).isEqualTo("1001");
        assertThat(request.getExternalEventId()).isEqualTo("evt-funding-1");
    }

    @Test
    void consumeStock_resolvesRecipientFromProductWhenMissing() {
        when(productClient.findItemSummary(44L))
                .thenReturn(new ProductClient.ProductItemSummary(44L, 99L, "테스트 상품"));

        String message = """
                {
                  "eventId": "evt-stock-1",
                  "eventType": "STOCK_DEPLETED",
                  "stockItemId": 55,
                  "itemId": 44
                }
                """;

        notificationEventConsumer.consumeStock(message);

        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationCommandService).createAndSend(captor.capture(), eq(null));

        CreateNotificationRequest request = captor.getValue();
        assertThat(request.getRecipientId()).isEqualTo(99L);
        assertThat(request.getType()).isEqualTo("STOCK_DEPLETED");
        assertThat(request.getReferenceType()).isEqualTo("ITEM");
        assertThat(request.getReferenceId()).isEqualTo("44");
    }
}
