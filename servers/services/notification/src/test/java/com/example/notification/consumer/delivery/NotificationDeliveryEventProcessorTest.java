package com.example.notification.consumer.delivery;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationDelivery;
import com.example.notification.entity.NotificationType;
import com.example.notification.repository.NotificationDeliveryRepository;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.service.delivery.NotificationDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryEventProcessorTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @Mock
    private NotificationDeliveryService notificationDeliveryService;

    private NotificationDeliveryEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new NotificationDeliveryEventProcessor(
                idempotentConsumerService,
                notificationRepository,
                notificationDeliveryRepository,
                notificationDeliveryService
        );
        when(idempotentConsumerService.executeIdempotent(any(), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    return Optional.ofNullable(supplier.get());
                });
    }

    @Test
    void process_dispatchesAndMarksAsDispatchedWhenDelivered() {
        Notification notification = Notification.create(
                10L,
                20L,
                NotificationType.GENERAL,
                NotificationChannel.IN_APP,
                "title",
                "message",
                null,
                null,
                "evt-1",
                "dedupe-1",
                null
        );
        ReflectionTestUtils.setField(notification, "id", 101L);

        NotificationDelivery delivered = NotificationDelivery.createPending(
                101L, NotificationChannel.IN_APP, "IN_APP", null
        );
        ReflectionTestUtils.setField(delivered, "id", 900L);
        delivered.markSent("IN_APP:900");
        delivered.markDelivered();

        when(notificationRepository.findById(101L)).thenReturn(Optional.of(notification));
        when(notificationDeliveryService.deliver(eq(notification), eq(NotificationChannel.IN_APP), any()))
                .thenReturn(delivered);
        when(notificationDeliveryRepository.findByNotificationIdOrderByIdAsc(101L))
                .thenReturn(List.of(delivered));

        String message = """
                {
                  "eventId": "delivery-evt-1",
                  "eventType": "NOTIFICATION_DELIVERY_REQUESTED",
                  "notificationId": 101,
                  "channel": "IN_APP"
                }
                """;

        processor.process(message, "delivery-evt-1", "NOTIFICATION_DELIVERY_REQUESTED");

        verify(notificationDeliveryService).deliver(eq(notification), eq(NotificationChannel.IN_APP), any());
        verify(notificationRepository).save(notification);
        assertThat(notification.getStatus().name()).isEqualTo("DISPATCHED");
    }

    @Test
    void process_marksAsFailedWhenAllDeliveriesFailed() {
        Notification notification = Notification.create(
                10L,
                20L,
                NotificationType.PAYMENT,
                NotificationChannel.EMAIL,
                "title",
                "message",
                null,
                null,
                "evt-2",
                "dedupe-2",
                null
        );
        ReflectionTestUtils.setField(notification, "id", 202L);

        NotificationDelivery failed = NotificationDelivery.createPending(
                202L, NotificationChannel.EMAIL, "EMAIL", "a@example.com"
        );
        ReflectionTestUtils.setField(failed, "id", 901L);
        failed.markFailed("EMAIL_SEND_FAILED", "smtp timeout");

        when(notificationRepository.findById(202L)).thenReturn(Optional.of(notification));
        when(notificationDeliveryService.deliver(eq(notification), eq(NotificationChannel.EMAIL), any()))
                .thenReturn(failed);
        when(notificationDeliveryRepository.findByNotificationIdOrderByIdAsc(202L))
                .thenReturn(List.of(failed));

        String message = """
                {
                  "eventId": "delivery-evt-2",
                  "eventType": "NOTIFICATION_DELIVERY_REQUESTED",
                  "notificationId": 202,
                  "channel": "EMAIL",
                  "emailTo": "a@example.com"
                }
                """;

        processor.process(message, "delivery-evt-2", "NOTIFICATION_DELIVERY_REQUESTED");

        verify(notificationRepository).save(notification);
        assertThat(notification.getStatus().name()).isEqualTo("FAILED");
    }

    @Test
    void process_throwsWhenChannelIsInvalid() {
        Notification notification = Notification.create(
                10L,
                20L,
                NotificationType.GENERAL,
                NotificationChannel.IN_APP,
                "title",
                "message",
                null,
                null,
                "evt-3",
                "dedupe-3",
                null
        );
        ReflectionTestUtils.setField(notification, "id", 303L);

        when(notificationRepository.findById(303L)).thenReturn(Optional.of(notification));

        String message = """
                {
                  "eventId": "delivery-evt-3",
                  "eventType": "NOTIFICATION_DELIVERY_REQUESTED",
                  "notificationId": 303,
                  "channel": "UNKNOWN"
                }
                """;

        assertThrows(IllegalArgumentException.class,
                () -> processor.process(message, "delivery-evt-3", "NOTIFICATION_DELIVERY_REQUESTED"));

        verify(notificationDeliveryService, never()).deliver(any(), any(), any());
        verify(notificationRepository, never()).save(any(Notification.class));
    }
}
