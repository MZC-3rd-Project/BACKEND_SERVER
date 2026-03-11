package com.example.notification.consumer.payment;

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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPaymentEventProcessorTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private NotificationCommandService notificationCommandService;

    @Mock
    private ProductItemSummaryClientFacade productClient;

    private NotificationPaymentEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new NotificationPaymentEventProcessor(
                idempotentConsumerService,
                new NotificationDispatchSupport(notificationCommandService, productClient)
        );
        lenient().when(idempotentConsumerService.executeIdempotent(any(), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    return Optional.ofNullable(supplier.get());
                });
    }

    @Test
    void process_dispatchesPaymentCompletedNotification() {
        String message = """
                {
                  "eventId": "evt-payment-1",
                  "eventType": "PAYMENT_COMPLETED",
                  "paymentId": 1,
                  "purchaseId": 2,
                  "userId": 3001
                }
                """;

        processor.process(message, "evt-payment-1", "PAYMENT_COMPLETED");

        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationCommandService).createAndSend(captor.capture(), eq(null));

        CreateNotificationRequest request = captor.getValue();
        assertThat(request.getRecipientId()).isEqualTo(3001L);
        assertThat(request.getType()).isEqualTo("PAYMENT");
        assertThat(request.getReferenceType()).isEqualTo("PURCHASE");
        assertThat(request.getReferenceId()).isEqualTo("2");
    }

    @Test
    void process_skipsWhenUserIdMissing() {
        String message = """
                {
                  "eventId": "evt-payment-2",
                  "eventType": "PAYMENT_COMPLETED",
                  "paymentId": 2
                }
                """;

        processor.process(message, "evt-payment-2", "PAYMENT_COMPLETED");

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(notificationCommandService);
    }
}
