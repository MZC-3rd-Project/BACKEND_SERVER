package com.example.notification.service.delivery;

import com.example.notification.config.NotificationDeliveryProperties;
import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationDelivery;
import com.example.notification.entity.NotificationDeliveryStatus;
import com.example.notification.repository.NotificationDeliveryRepository;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.service.email.EmailSendResult;
import com.example.notification.service.email.NotificationEmailService;
import com.example.notification.service.realtime.SseEventPublisher;
import com.example.notification.service.realtime.SseNotificationEvent;
import com.example.notification.service.setting.NotificationSettingService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDeliveryService {

    private static final String CODE_DISABLED_BY_POLICY = "DISABLED_BY_POLICY";
    private static final String CODE_MISSING_EMAIL = "MISSING_EMAIL";
    private static final String CODE_NOT_IMPLEMENTED = "NOT_IMPLEMENTED";
    private static final String CODE_EMAIL_FAILED = "EMAIL_SEND_FAILED";
    private static final String CODE_EMAIL_SKIPPED = "EMAIL_SKIPPED";
    private static final String CODE_SSE_FAILED = "SSE_SEND_FAILED";
    private static final String CODE_NOTIFICATION_NOT_FOUND = "NOTIFICATION_NOT_FOUND";
    private static final Set<NotificationDeliveryStatus> CLAIMABLE_STATUSES = Set.of(
            NotificationDeliveryStatus.PENDING,
            NotificationDeliveryStatus.RETRYING
    );

    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationSettingService notificationSettingService;
    private final NotificationEmailService notificationEmailService;
    private final SseEventPublisher sseEventPublisher;
    private final NotificationDeliveryProperties notificationDeliveryProperties;

    public NotificationDelivery deliver(Notification notification,
                                        NotificationChannel channel,
                                        NotificationDeliveryCommand command) {
        NotificationDelivery prepared = prepareDelivery(notification.getId(), channel, command.getEmailTo());
        if (isTerminal(prepared.getStatus())) {
            return prepared;
        }
        return dispatchExisting(prepared.getId());
    }

    public List<NotificationDelivery> dispatchPendingByNotificationId(Long notificationId, String fallbackEmailTo) {
        List<NotificationDelivery> deliveries = notificationDeliveryRepository.findByNotificationIdOrderByIdAsc(notificationId);
        if (deliveries.isEmpty()) {
            return List.of();
        }

        List<NotificationDelivery> dispatched = new ArrayList<>();
        for (NotificationDelivery delivery : deliveries) {
            if (isTerminal(delivery.getStatus())) {
                continue;
            }
            if (delivery.getStatus() == NotificationDeliveryStatus.FAILED
                    && delivery.getAttemptCount() >= maxAttempts()) {
                continue;
            }

            NotificationDelivery prepared = prepareDelivery(
                    delivery.getNotificationId(),
                    delivery.getChannel(),
                    hasText(delivery.getRecipientAddress()) ? delivery.getRecipientAddress() : fallbackEmailTo
            );
            dispatched.add(dispatchExisting(prepared.getId()));
        }
        return dispatched;
    }

    public void dispatchRetryTargets() {
        Set<NotificationDeliveryStatus> statuses = Set.of(
                NotificationDeliveryStatus.PENDING,
                NotificationDeliveryStatus.RETRYING
        );
        List<NotificationDelivery> targets = notificationDeliveryRepository.findDispatchTargets(
                statuses,
                LocalDateTime.now(),
                PageRequest.of(0, Math.max(1, notificationDeliveryProperties.getRetryBatchSize()))
        );
        for (NotificationDelivery target : targets) {
            dispatchExisting(target.getId());
        }
    }

    public NotificationDelivery dispatchExisting(Long deliveryId) {
        NotificationDelivery delivery = notificationDeliveryRepository.findById(deliveryId).orElse(null);
        if (delivery == null) {
            return null;
        }
        if (isTerminal(delivery.getStatus())) {
            return delivery;
        }
        if (!claimDeliveryForDispatch(deliveryId)) {
            return notificationDeliveryRepository.findById(deliveryId).orElse(null);
        }
        delivery = notificationDeliveryRepository.findById(deliveryId).orElse(null);
        if (delivery == null) {
            return null;
        }

        Notification notification = notificationRepository.findById(delivery.getNotificationId()).orElse(null);
        if (notification == null) {
            return applyOutcome(deliveryId, DeliveryOutcome.dropped(
                    delivery.getProvider(),
                    CODE_NOTIFICATION_NOT_FOUND,
                    "notification not found"
            ));
        }

        NotificationDeliveryCommand command = NotificationDeliveryCommand.builder()
                .userId(notification.getRecipientId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .emailTo(delivery.getRecipientAddress())
                .build();

        DeliveryOutcome outcome = send(notification, delivery.getChannel(), command, delivery.getId());
        return applyOutcome(delivery.getId(), outcome);
    }

    @Transactional
    protected NotificationDelivery prepareDelivery(Long notificationId,
                                                   NotificationChannel channel,
                                                   String emailTo) {
        NotificationDelivery delivery = notificationDeliveryRepository
                .findByNotificationIdAndChannel(notificationId, channel)
                .orElseGet(() -> NotificationDelivery.createPending(notificationId, channel, channel.name(), emailTo));

        delivery.updateRecipientAddress(emailTo);

        if (!isTerminal(delivery.getStatus()) && !isClaimLocked(delivery)) {
            delivery.markPending();
        }
        return notificationDeliveryRepository.save(delivery);
    }

    private DeliveryOutcome send(Notification notification,
                                 NotificationChannel channel,
                                 NotificationDeliveryCommand command,
                                 Long deliveryId) {
        if (!notificationSettingService.shouldSendNotification(command.getUserId(), command.getType(), channel)) {
            return DeliveryOutcome.dropped(channel.name(), CODE_DISABLED_BY_POLICY, "notification disabled by preference");
        }

        return switch (channel) {
            case IN_APP -> sendInApp(notification, deliveryId);
            case EMAIL -> sendEmail(command, channel.name());
            case SMS, KAKAO, PUSH -> DeliveryOutcome.dropped(
                    channel.name(),
                    CODE_NOT_IMPLEMENTED,
                    "channel delivery not implemented yet"
            );
        };
    }

    private DeliveryOutcome sendInApp(Notification notification, Long deliveryId) {
        try {
            sseEventPublisher.publish(SseNotificationEvent.from(notification));
            return DeliveryOutcome.delivered("IN_APP", "IN_APP:" + deliveryId);
        } catch (Exception e) {
            return DeliveryOutcome.failed("IN_APP", CODE_SSE_FAILED, safeMessage(e), true);
        }
    }

    private DeliveryOutcome sendEmail(NotificationDeliveryCommand command, String defaultProvider) {
        if (!hasText(command.getEmailTo())) {
            return DeliveryOutcome.dropped(defaultProvider, CODE_MISSING_EMAIL, "email recipient is empty");
        }

        EmailSendResult emailResult = notificationEmailService.send(
                command.getUserId(),
                command.getType(),
                command.getEmailTo(),
                command.getTitle(),
                command.getMessage(),
                null
        );

        String provider = hasText(emailResult.getProvider()) ? emailResult.getProvider() : defaultProvider;
        if (emailResult.isSuccess()) {
            return DeliveryOutcome.delivered(provider, emailResult.getMessageId());
        }
        if (emailResult.isSkipped()) {
            return DeliveryOutcome.dropped(provider, CODE_EMAIL_SKIPPED, emailResult.getErrorMessage());
        }
        return DeliveryOutcome.failed(provider, CODE_EMAIL_FAILED, emailResult.getErrorMessage(), true);
    }

    @Transactional
    protected NotificationDelivery applyOutcome(Long deliveryId, DeliveryOutcome outcome) {
        NotificationDelivery delivery = notificationDeliveryRepository.findById(deliveryId).orElse(null);
        if (delivery == null) {
            return null;
        }

        delivery.updateProvider(outcome.provider());
        switch (outcome.status()) {
            case DELIVERED -> {
                delivery.markSent(outcome.providerMessageId());
                delivery.markDelivered();
                log.info("Notification delivery completed. deliveryId={}, notificationId={}, channel={}, provider={}",
                        delivery.getId(), delivery.getNotificationId(), delivery.getChannel(), outcome.provider());
            }
            case DROPPED -> {
                delivery.markDropped(outcome.errorCode(), outcome.errorMessage());
                log.warn("Notification delivery dropped. deliveryId={}, notificationId={}, channel={}, errorCode={}",
                        delivery.getId(), delivery.getNotificationId(), delivery.getChannel(), outcome.errorCode());
                incrementCounter("notification.delivery.dropped.total", delivery, outcome.errorCode());
            }
            case FAILED -> {
                applyFailure(delivery, outcome);
                if (delivery.getStatus() == NotificationDeliveryStatus.FAILED) {
                    log.error("Notification delivery permanently failed. deliveryId={}, notificationId={}, channel={}, attempts={}, errorCode={}, errorMessage={}",
                            delivery.getId(),
                            delivery.getNotificationId(),
                            delivery.getChannel(),
                            delivery.getAttemptCount(),
                            outcome.errorCode(),
                            outcome.errorMessage());
                    incrementCounter("notification.delivery.failed.total", delivery, outcome.errorCode());
                } else {
                    incrementCounter("notification.delivery.retry.scheduled.total", delivery, outcome.errorCode());
                }
            }
        }
        return notificationDeliveryRepository.save(delivery);
    }

    private void applyFailure(NotificationDelivery delivery, DeliveryOutcome outcome) {
        int nextAttempt = delivery.getAttemptCount() + 1;
        if (outcome.retryable() && nextAttempt < maxAttempts()) {
            delivery.markRetry(outcome.errorCode(), outcome.errorMessage(), calculateNextRetryAt(nextAttempt));
            return;
        }
        delivery.markFailed(outcome.errorCode(), outcome.errorMessage());
    }

    private LocalDateTime calculateNextRetryAt(int nextAttempt) {
        long delay = Math.max(1, notificationDeliveryProperties.getInitialBackoffSeconds());
        int multiplier = Math.max(1, notificationDeliveryProperties.getBackoffMultiplier());
        for (int i = 1; i < nextAttempt; i++) {
            delay = Math.min(delay * multiplier, 24L * 60L * 60L);
        }
        return LocalDateTime.now().plusSeconds(delay);
    }

    private int maxAttempts() {
        return Math.max(1, notificationDeliveryProperties.getMaxAttempts());
    }

    @Transactional
    protected boolean claimDeliveryForDispatch(Long deliveryId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseUntil = now.plusSeconds(Math.max(1, notificationDeliveryProperties.getClaimLeaseSeconds()));
        return notificationDeliveryRepository.claimDispatchLock(
                deliveryId,
                CLAIMABLE_STATUSES,
                now,
                leaseUntil
        ) > 0;
    }

    private boolean isTerminal(NotificationDeliveryStatus status) {
        return status == NotificationDeliveryStatus.DELIVERED
                || status == NotificationDeliveryStatus.DROPPED;
    }

    private boolean isClaimLocked(NotificationDelivery delivery) {
        if (!CLAIMABLE_STATUSES.contains(delivery.getStatus())) {
            return false;
        }
        LocalDateTime nextRetryAt = delivery.getNextRetryAt();
        return nextRetryAt != null && nextRetryAt.isAfter(LocalDateTime.now());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return throwable == null ? "unknown error" : throwable.getClass().getSimpleName();
        }
        String message = throwable.getMessage();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private void incrementCounter(String metricName, NotificationDelivery delivery, String errorCode) {
        Counter.builder(metricName)
                .tag("channel", delivery.getChannel().name())
                .tag("provider", safeTagValue(delivery.getProvider(), "UNKNOWN"))
                .tag("error_code", safeTagValue(errorCode, "NONE"))
                .register(Metrics.globalRegistry)
                .increment();
    }

    private String safeTagValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private record DeliveryOutcome(
            DeliveryStatus status,
            String provider,
            String providerMessageId,
            String errorCode,
            String errorMessage,
            boolean retryable
    ) {
        private static DeliveryOutcome delivered(String provider, String providerMessageId) {
            return new DeliveryOutcome(DeliveryStatus.DELIVERED, provider, providerMessageId, null, null, false);
        }

        private static DeliveryOutcome dropped(String provider, String errorCode, String errorMessage) {
            return new DeliveryOutcome(DeliveryStatus.DROPPED, provider, null, errorCode, errorMessage, false);
        }

        private static DeliveryOutcome failed(String provider, String errorCode, String errorMessage, boolean retryable) {
            return new DeliveryOutcome(DeliveryStatus.FAILED, provider, null, errorCode, errorMessage, retryable);
        }
    }

    private enum DeliveryStatus {
        DELIVERED,
        DROPPED,
        FAILED
    }
}
