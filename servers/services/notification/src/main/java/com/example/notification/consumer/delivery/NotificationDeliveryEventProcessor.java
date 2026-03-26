package com.example.notification.consumer.delivery;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationDelivery;
import com.example.notification.entity.NotificationDeliveryStatus;
import com.example.notification.entity.NotificationStatus;
import com.example.notification.repository.NotificationDeliveryRepository;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.service.delivery.NotificationDeliveryCommand;
import com.example.notification.service.delivery.NotificationDeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = NotificationDeliveryEventProcessor.CONSUMER_NAME)
public class NotificationDeliveryEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "notification-delivery-events-consumer";
    private static final String EVENT_TYPE = "NOTIFICATION_DELIVERY_REQUESTED";

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final NotificationDeliveryService notificationDeliveryService;
    private final Map<String, EventSpec<NotificationDeliveryEventMessage>> eventSpecs;

    public NotificationDeliveryEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            NotificationRepository notificationRepository,
            NotificationDeliveryRepository notificationDeliveryRepository,
            NotificationDeliveryService notificationDeliveryService
    ) {
        super(idempotentConsumerService);
        this.notificationRepository = notificationRepository;
        this.notificationDeliveryRepository = notificationDeliveryRepository;
        this.notificationDeliveryService = notificationDeliveryService;
        this.eventSpecs = Map.of(
                EVENT_TYPE,
                EventSpec.of(
                        NotificationDeliveryEventMessage.class,
                        event -> true,
                        this::handleDeliveryRequested
                )
        );
    }

    @Override
    protected String idempotentEventType() {
        return EVENT_TYPE;
    }

    @Override
    protected Map<String, EventSpec<NotificationDeliveryEventMessage>> eventSpecs() {
        return eventSpecs;
    }

    @Override
    protected void onInvalidEnvelope(String eventId, String eventType, String message) {
        log.warn("Skip invalid delivery event. eventId={}, eventType={}", eventId, eventType);
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

        log.info("Notification delivery processed. notificationId={}, channel={}",
                notification.getId(), channel);

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
                log.info("Notification status updated to DISPATCHED. notificationId={}", notificationId);
            }
            return;
        }

        boolean allFailed = deliveries.stream().allMatch(this::isFinalFailure);
        if (allFailed && notification.getStatus() == NotificationStatus.CREATED) {
            notification.markAsFailed();
            notificationRepository.save(notification);
            log.warn("Notification status updated to FAILED after delivery attempts. notificationId={}", notificationId);
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
}
