package com.example.notification.service.delivery;

import com.example.notification.config.NotificationDeliveryProperties;
import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationDelivery;
import com.example.notification.entity.NotificationDeliveryStatus;
import com.example.notification.entity.NotificationType;
import com.example.notification.repository.NotificationDeliveryRepository;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.service.email.EmailSendResult;
import com.example.notification.service.email.NotificationEmailService;
import com.example.notification.service.realtime.SseEventPublisher;
import com.example.notification.service.setting.NotificationSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

    @Mock
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationSettingService notificationSettingService;

    @Mock
    private NotificationEmailService notificationEmailService;

    @Mock
    private SseEventPublisher sseEventPublisher;

    private NotificationDeliveryService notificationDeliveryService;

    @BeforeEach
    void setUp() {
        NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
        properties.setMaxAttempts(3);
        properties.setInitialBackoffSeconds(10);
        properties.setBackoffMultiplier(2);
        properties.setRetryBatchSize(100);
        properties.setClaimLeaseSeconds(30);

        notificationDeliveryService = new NotificationDeliveryService(
                notificationDeliveryRepository,
                notificationRepository,
                notificationSettingService,
                notificationEmailService,
                sseEventPublisher,
                properties
        );

        lenient().when(notificationDeliveryRepository.save(any(NotificationDelivery.class))).thenAnswer(invocation -> {
            NotificationDelivery delivery = invocation.getArgument(0);
            if (delivery.getId() == null) {
                ReflectionTestUtils.setField(delivery, "id", 1L);
            }
            return delivery;
        });
    }

    @Test
    void deliver_emailFailure_marksRetrying() {
        Notification notification = Notification.create(
                10L,
                10L,
                NotificationType.PAYMENT,
                NotificationChannel.EMAIL,
                "title",
                "message",
                null,
                null,
                "evt-1",
                "dedupe-1",
                null
        );
        ReflectionTestUtils.setField(notification, "id", 99L);

        when(notificationDeliveryRepository.findByNotificationIdAndChannel(99L, NotificationChannel.EMAIL))
                .thenReturn(Optional.empty());
        when(notificationDeliveryRepository.claimDispatchLock(eq(1L), any(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);
        when(notificationDeliveryRepository.findById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            NotificationDelivery pending = NotificationDelivery.createPending(
                    99L,
                    NotificationChannel.EMAIL,
                    "EMAIL",
                    "user@example.com"
            );
            ReflectionTestUtils.setField(pending, "id", id);
            return Optional.of(pending);
        });
        when(notificationRepository.findById(99L)).thenReturn(Optional.of(notification));
        when(notificationSettingService.shouldSendNotification(10L, NotificationType.PAYMENT, NotificationChannel.EMAIL))
                .thenReturn(true);
        when(notificationEmailService.send(10L, NotificationType.PAYMENT, "user@example.com", "title", "message", null))
                .thenReturn(EmailSendResult.failure("SMTP", "smtp timeout"));

        NotificationDelivery delivered = notificationDeliveryService.deliver(
                notification,
                NotificationChannel.EMAIL,
                NotificationDeliveryCommand.builder()
                        .userId(10L)
                        .type(NotificationType.PAYMENT)
                        .title("title")
                        .message("message")
                        .emailTo("user@example.com")
                        .build()
        );

        assertThat(delivered.getStatus()).isEqualTo(NotificationDeliveryStatus.RETRYING);
        assertThat(delivered.getNextRetryAt()).isNotNull();
    }

    @Test
    void dispatchRetryTargets_retriesAndDelivers() {
        Notification notification = Notification.create(
                10L,
                10L,
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
        ReflectionTestUtils.setField(notification, "id", 99L);

        NotificationDelivery retryTarget = NotificationDelivery.createPending(
                99L,
                NotificationChannel.EMAIL,
                "EMAIL",
                "user@example.com"
        );
        ReflectionTestUtils.setField(retryTarget, "id", 77L);
        retryTarget.markRetry("ERR", "failed", LocalDateTime.now().minusSeconds(1));

        when(notificationDeliveryRepository.findDispatchTargets(
                eq(Set.of(NotificationDeliveryStatus.PENDING, NotificationDeliveryStatus.RETRYING)),
                any(LocalDateTime.class),
                any()
        )).thenReturn(List.of(retryTarget));
        when(notificationDeliveryRepository.findById(77L)).thenReturn(Optional.of(retryTarget));
        when(notificationDeliveryRepository.claimDispatchLock(eq(77L), any(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);
        when(notificationRepository.findById(99L)).thenReturn(Optional.of(notification));
        when(notificationSettingService.shouldSendNotification(10L, NotificationType.PAYMENT, NotificationChannel.EMAIL))
                .thenReturn(true);
        when(notificationEmailService.send(10L, NotificationType.PAYMENT, "user@example.com", "title", "message", null))
                .thenReturn(EmailSendResult.success("SMTP", "msg-1"));

        notificationDeliveryService.dispatchRetryTargets();

        assertThat(retryTarget.getStatus()).isEqualTo(NotificationDeliveryStatus.DELIVERED);
    }

    @Test
    void dispatchExisting_dropsWhenNotificationMissing() {
        NotificationDelivery pending = NotificationDelivery.createPending(
                999L,
                NotificationChannel.IN_APP,
                "IN_APP",
                null
        );
        ReflectionTestUtils.setField(pending, "id", 55L);

        when(notificationDeliveryRepository.findById(55L)).thenReturn(Optional.of(pending));
        when(notificationDeliveryRepository.claimDispatchLock(eq(55L), any(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);
        when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

        NotificationDelivery result = notificationDeliveryService.dispatchExisting(55L);

        assertThat(result.getStatus()).isEqualTo(NotificationDeliveryStatus.DROPPED);
    }

    @Test
    void dispatchExisting_skipsWhenClaimLost() {
        NotificationDelivery pending = NotificationDelivery.createPending(
                999L,
                NotificationChannel.IN_APP,
                "IN_APP",
                null
        );
        ReflectionTestUtils.setField(pending, "id", 66L);

        when(notificationDeliveryRepository.findById(66L)).thenReturn(Optional.of(pending));
        when(notificationDeliveryRepository.claimDispatchLock(eq(66L), any(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0);

        NotificationDelivery result = notificationDeliveryService.dispatchExisting(66L);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
    }
}
