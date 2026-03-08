package com.example.notification.consumer.funding;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationFundingEventProcessorTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private NotificationCommandService notificationCommandService;

    @Mock
    private ProductItemSummaryClientFacade productClient;

    private NotificationFundingEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new NotificationFundingEventProcessor(
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
    void process_dispatchesFundingSuccessNotification() {
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

        processor.process(message, "evt-funding-1", "FUNDING_SUCCEEDED");

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
    void process_skipsWhenSellerIdMissing() {
        String message = """
                {
                  "eventId": "evt-funding-2",
                  "eventType": "FUNDING_FAILED",
                  "campaignId": 1002
                }
                """;

        processor.process(message, "evt-funding-2", "FUNDING_FAILED");

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(notificationCommandService);
    }
}
