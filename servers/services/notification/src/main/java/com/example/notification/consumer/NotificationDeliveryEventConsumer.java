package com.example.notification.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.util.JsonUtils;
import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationDelivery;
import com.example.notification.entity.NotificationDeliveryStatus;
import com.example.notification.entity.NotificationStatus;
import com.example.notification.repository.NotificationDeliveryRepository;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.service.delivery.NotificationDeliveryCommand;
import com.example.notification.service.delivery.NotificationDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDeliveryEventConsumer {

    private static final String EVENT_TYPE = "NOTIFICATION_DELIVERY_REQUESTED";

    private final IdempotentConsumerService idempotentConsumerService;
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final NotificationDeliveryService notificationDeliveryService;

    @KafkaListener(topics = "notification-delivery-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consumeDispatchRequest(String message) {
        NotificationDeliveryEventMessage event = JsonUtils.fromJson(message, NotificationDeliveryEventMessage.class);
        if (!hasRequiredEnvelope(event)) {
            return;
        }

        idempotentConsumerService.executeIdempotent(event.getEventId(), EVENT_TYPE, () -> {
            if (!EVENT_TYPE.equals(normalizeEventType(event.getEventType()))) {
                log.debug("Ignore delivery event type: {}", event.getEventType());
                return null;
            }
            handleDeliveryRequested(event);
            return null;
        });
    }

    private void handleDeliveryRequested(NotificationDeliveryEventMessage event) {
        if (event.getNotificationId() == null) {
            throw new IllegalArgumentException("notificationId is required");
        }

        Notification notification = notificationRepository.findById(event.getNotificationId()).orElse(null);
        if (notification == null) {
            throw new IllegalStateException("notification not found. notificationId=" + event.getNotificationId());
        }

        NotificationChannel channel = resolveChannel(event.getChannel());
        if (channel == null) {
            throw new IllegalArgumentException(
                    "invalid channel. eventId=" + event.getEventId() + ", channel=" + event.getChannel()
            );
        }

        notificationDeliveryService.deliver(
                notification,
                channel,
                NotificationDeliveryCommand.builder()
                        .userId(notification.getRecipientId())
                        .type(notification.getType())
                        .title(notification.getTitle())
                        .message(notification.getMessage())
                        .emailTo(event.getEmailTo())
                        .build()
        );

        refreshNotificationStatus(notification.getId());
    }

    private void refreshNotificationStatus(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null) {
            return;
        }

        List<NotificationDelivery> deliveries = notificationDeliveryRepository.findByNotificationIdOrderByIdAsc(notificationId);
        if (deliveries.isEmpty()) {
            return;
        }

        boolean anyDelivered = deliveries.stream()
                .anyMatch(delivery -> delivery.getStatus() == NotificationDeliveryStatus.DELIVERED);

        if (anyDelivered) {
            if (notification.getStatus() == NotificationStatus.CREATED
                    || notification.getStatus() == NotificationStatus.FAILED) {
                notification.markAsDispatched();
                notificationRepository.save(notification);
            }
            return;
        }

        boolean allFailed = deliveries.stream().allMatch(this::isFinalFailure);
        if (allFailed && notification.getStatus() == NotificationStatus.CREATED) {
            notification.markAsFailed();
            notificationRepository.save(notification);
        }
    }

    private NotificationChannel resolveChannel(String rawChannel) {
        if (rawChannel == null || rawChannel.isBlank()) {
            return null;
        }
        try {
            return NotificationChannel.valueOf(rawChannel.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isFinalFailure(NotificationDelivery delivery) {
        NotificationDeliveryStatus status = delivery.getStatus();
        return status == NotificationDeliveryStatus.FAILED
                || status == NotificationDeliveryStatus.DROPPED
                || status == NotificationDeliveryStatus.BOUNCED;
    }

    private boolean hasRequiredEnvelope(NotificationDeliveryEventMessage event) {
        if (event == null) {
            log.warn("Skip invalid delivery event. payload is null");
            return false;
        }
        if (event.getEventId() == null || event.getEventType() == null) {
            log.warn("Skip invalid delivery event. eventId={}, eventType={}",
                    event.getEventId(), event.getEventType());
            return false;
        }
        return true;
    }

    private String normalizeEventType(String eventType) {
        return eventType == null ? "" : eventType.toUpperCase(Locale.ROOT);
    }
}
