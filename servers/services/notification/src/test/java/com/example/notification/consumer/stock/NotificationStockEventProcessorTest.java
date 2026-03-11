package com.example.notification.consumer.stock;

import com.example.clients.product.dto.ProductItemSummary;
import com.example.clients.product.facade.ProductItemSummaryClientFacade;
import com.example.config.kafka.IdempotentConsumerService;
import com.example.notification.consumer.support.NotificationDispatchSupport;
import com.example.notification.dto.command.request.CreateNotificationRequest;
import com.example.notification.service.command.NotificationCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationStockEventProcessorTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private NotificationCommandService notificationCommandService;

    @Mock
    private ProductItemSummaryClientFacade productClient;

    private NotificationStockEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new NotificationStockEventProcessor(
                idempotentConsumerService,
                new NotificationDispatchSupport(notificationCommandService, productClient)
        );
        when(idempotentConsumerService.executeIdempotent(any(), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    return Optional.ofNullable(supplier.get());
                });
    }

    @Test
    void process_resolvesRecipientFromProductWhenMissing() {
        when(productClient.findItemSummary(44L))
                .thenReturn(new ProductItemSummary(44L, 99L, "테스트 상품"));

        String message = """
                {
                  "eventId": "evt-stock-1",
                  "eventType": "STOCK_DEPLETED",
                  "stockItemId": 55,
                  "itemId": 44
                }
                """;

        processor.process(message, "evt-stock-1", "STOCK_DEPLETED");

        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationCommandService).createAndSend(captor.capture(), eq(null));

        CreateNotificationRequest request = captor.getValue();
        assertThat(request.getRecipientId()).isEqualTo(99L);
        assertThat(request.getType()).isEqualTo("STOCK_DEPLETED");
        assertThat(request.getReferenceType()).isEqualTo("ITEM");
        assertThat(request.getReferenceId()).isEqualTo("44");
    }
}
