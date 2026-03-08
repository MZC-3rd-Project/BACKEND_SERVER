package com.example.notification.consumer.chat;

import com.example.clients.product.facade.ProductItemSummaryClientFacade;
import com.example.config.kafka.IdempotentConsumerService;
import com.example.notification.consumer.support.NotificationDispatchSupport;
import com.example.notification.dto.command.request.CreateNotificationRequest;
import com.example.notification.service.command.NotificationCommandService;
import com.example.notification.service.setting.NotificationSettingService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatNotificationEventProcessorTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private NotificationCommandService notificationCommandService;

    @Mock
    private ProductItemSummaryClientFacade productClient;

    @Mock
    private NotificationSettingService notificationSettingService;

    private ChatNotificationEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ChatNotificationEventProcessor(
                idempotentConsumerService,
                new NotificationDispatchSupport(notificationCommandService, productClient),
                notificationSettingService
        );
        lenient().when(idempotentConsumerService.executeIdempotent(any(), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    return Optional.ofNullable(supplier.get());
                });
    }

    @Test
    void process_dispatchesNotificationWhenSettingEnabled() {
        when(notificationSettingService.shouldSendNotification(anyLong(), any(), any())).thenReturn(true);

        String message = """
                {
                  "eventId": "evt-chat-1",
                  "eventType": "CHAT_NOTIFICATION_REQUESTED",
                  "recipientId": 5001,
                  "roomId": 7001,
                  "roomType": "FUNDING_GROUP",
                  "messageId": 9001,
                  "senderId": 3001,
                  "messageType": "CHAT",
                  "preview": "안녕하세요"
                }
                """;

        processor.process(message, "evt-chat-1", "CHAT_NOTIFICATION_REQUESTED");

        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationCommandService).createAndSend(captor.capture(), eq(null));

        CreateNotificationRequest request = captor.getValue();
        assertThat(request.getRecipientId()).isEqualTo(5001L);
        assertThat(request.getType()).isEqualTo("CHAT_MESSAGE");
        assertThat(request.getReferenceType()).isEqualTo("CHAT_ROOM");
        assertThat(request.getReferenceId()).isEqualTo("7001");
    }

    @Test
    void process_skipsWhenSettingDisabled() {
        when(notificationSettingService.shouldSendNotification(anyLong(), any(), any())).thenReturn(false);

        String message = """
                {
                  "eventId": "evt-chat-2",
                  "eventType": "CHAT_NOTIFICATION_REQUESTED",
                  "recipientId": 5002,
                  "roomId": 7002,
                  "preview": "test"
                }
                """;

        processor.process(message, "evt-chat-2", "CHAT_NOTIFICATION_REQUESTED");

        verify(notificationCommandService, never()).createAndSend(any(), eq(null));
    }
}
