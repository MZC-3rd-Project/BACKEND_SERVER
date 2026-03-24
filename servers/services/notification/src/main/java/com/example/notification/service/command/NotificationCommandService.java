package com.example.notification.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.notification.dto.command.request.CreateNotificationRequest;
import com.example.notification.dto.command.response.NotificationDispatchResponse;
import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationTemplate;
import com.example.notification.entity.NotificationType;
import com.example.notification.event.NotificationDeliveryRequestedEvent;
import com.example.notification.exception.NotificationErrorCode;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.service.content.NotificationContentSanitizer;
import com.example.notification.service.template.NotificationTemplateResolver;
import com.example.notification.service.template.TemplateRenderService;
import com.example.notification.service.unread.NotificationUnreadCountService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private static final String DEFAULT_LOCALE = "ko-KR";
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_REFERENCE_TYPE_LENGTH = 50;
    private static final int MAX_REFERENCE_ID_LENGTH = 100;
    private static final int MAX_EXTERNAL_EVENT_ID_LENGTH = 100;

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateResolver notificationTemplateResolver;
    private final TemplateRenderService templateRenderService;
    private final NotificationContentSanitizer notificationContentSanitizer;
    private final EventPublisher eventPublisher;
    private final NotificationUnreadCountService notificationUnreadCountService;

    @Transactional
    public NotificationDispatchResponse createAndSend(CreateNotificationRequest request, Long requestUserId) {
        validateRequesterContext(request, requestUserId);

        NotificationType type = parseType(request.getType());
        List<NotificationChannel> channels = parseChannels(request.getChannels());

        String dedupeKey = resolveDedupeKey(request, type);
        Optional<Notification> existing = notificationRepository.findByDedupeKey(dedupeKey);
        if (existing.isPresent()) {
            publishDeliveryRequests(existing.get(), channels, request.getEmailTo());
            log.info("Notification deduplicated. notificationId={}, recipientId={}, channelCount={}",
                    existing.get().getId(), request.getRecipientId(), channels.size());
            return NotificationDispatchResponse.deduplicated(existing.get().getId(), existing.get().getStatus().name());
        }

        NotificationChannel primaryChannel = channels.get(0);
        Map<String, Object> variables = notificationContentSanitizer.sanitizeVariables(request.getVariables());
        NotificationTemplate template = resolveTemplate(request, type, primaryChannel);

        String title = sanitizeAndLimit(resolveTitle(request, template, variables), MAX_TITLE_LENGTH);
        String message = sanitizeAndLimit(resolveMessage(request, template, variables), MAX_MESSAGE_LENGTH);
        Map<String, Object> payload = copyPayload(variables);
        String referenceType = sanitizeAndLimit(request.getReferenceType(), MAX_REFERENCE_TYPE_LENGTH);
        String referenceId = sanitizeAndLimit(request.getReferenceId(), MAX_REFERENCE_ID_LENGTH);
        String externalEventId = sanitizeAndLimit(request.getExternalEventId(), MAX_EXTERNAL_EVENT_ID_LENGTH);

        Long actorId = resolveActorId(request, requestUserId);
        Notification notification = Notification.create(
                request.getRecipientId(),
                actorId,
                type,
                primaryChannel,
                title,
                message,
                referenceType,
                referenceId,
                externalEventId,
                dedupeKey,
                payload
        );

        try {
            notificationRepository.save(notification);
            notificationUnreadCountService.increase(request.getRecipientId());
        } catch (DataIntegrityViolationException e) {
            Notification conflict = notificationRepository.findByDedupeKey(dedupeKey)
                    .orElseThrow(() -> e);
            publishDeliveryRequests(conflict, channels, request.getEmailTo());
            log.info("Notification deduplicated after race. notificationId={}, recipientId={}, channelCount={}",
                    conflict.getId(), request.getRecipientId(), channels.size());
            return NotificationDispatchResponse.deduplicated(conflict.getId(), conflict.getStatus().name());
        }

        publishDeliveryRequests(notification, channels, request.getEmailTo());
        log.info("Notification created and dispatch requested. notificationId={}, recipientId={}, channelCount={}",
                notification.getId(), request.getRecipientId(), channels.size());

        return NotificationDispatchResponse.created(
                notification.getId(),
                notification.getStatus().name(),
                List.of(),
                List.of()
        );
    }

    private void publishDeliveryRequests(Notification notification,
                                         List<NotificationChannel> channels,
                                         String emailTo) {
        for (NotificationChannel channel : channels) {
            eventPublisher.publish(
                    new NotificationDeliveryRequestedEvent(notification.getId(), channel, emailTo),
                    EventMetadata.of("Notification", String.valueOf(notification.getId()))
            );
            log.debug("Notification delivery event published. notificationId={}, channel={}",
                    notification.getId(), channel);
        }
    }

    private NotificationTemplate resolveTemplate(CreateNotificationRequest request,
                                                 NotificationType type,
                                                 NotificationChannel channel) {
        String locale = request.getLocale() == null || request.getLocale().isBlank()
                ? DEFAULT_LOCALE
                : request.getLocale();

        return notificationTemplateResolver.resolve(type, channel, locale)
                .orElseGet(() -> {
                    if (hasManualContent(request)) {
                        return null;
                    }
                    throw new BusinessException(NotificationErrorCode.TEMPLATE_NOT_FOUND);
                });
    }

    private String resolveTitle(CreateNotificationRequest request,
                                NotificationTemplate template,
                                Map<String, Object> variables) {
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            return request.getTitle();
        }
        if (template == null) {
            return "";
        }
        return templateRenderService.render(template.getTitleTemplate(), variables);
    }

    private String resolveMessage(CreateNotificationRequest request,
                                  NotificationTemplate template,
                                  Map<String, Object> variables) {
        if (request.getMessage() != null && !request.getMessage().isBlank()) {
            return request.getMessage();
        }
        if (template == null) {
            return "";
        }
        return templateRenderService.render(template.getMessageTemplate(), variables);
    }

    private String resolveDedupeKey(CreateNotificationRequest request, NotificationType type) {
        if (request.getDedupeKey() != null && !request.getDedupeKey().isBlank()) {
            return request.getDedupeKey();
        }

        String event = safeOrDefault(request.getExternalEventId(), "no-event");
        String refType = safeOrDefault(request.getReferenceType(), "no-ref-type");
        String refId = safeOrDefault(request.getReferenceId(), "no-ref-id");
        return request.getRecipientId() + ":" + type.name() + ":" + event + ":" + refType + ":" + refId;
    }

    private Map<String, Object> copyPayload(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return null;
        }
        return new LinkedHashMap<>(variables);
    }

    private String sanitizeAndLimit(String value, int maxLength) {
        String sanitized = notificationContentSanitizer.sanitizeText(value);
        if (sanitized == null || sanitized.length() <= maxLength) {
            return sanitized;
        }
        return sanitized.substring(0, maxLength);
    }

    private NotificationType parseType(String rawType) {
        try {
            return NotificationType.valueOf(rawType.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            throw new BusinessException(NotificationErrorCode.INVALID_NOTIFICATION_TYPE);
        }
    }

    private List<NotificationChannel> parseChannels(List<String> rawChannels) {
        if (rawChannels == null || rawChannels.isEmpty()) {
            return List.of(NotificationChannel.IN_APP);
        }

        Set<NotificationChannel> uniqueChannels = new LinkedHashSet<>();
        for (String rawChannel : rawChannels) {
            try {
                uniqueChannels.add(NotificationChannel.valueOf(rawChannel.toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {
                throw new BusinessException(NotificationErrorCode.INVALID_CHANNEL);
            }
        }

        if (uniqueChannels.isEmpty()) {
            uniqueChannels.add(NotificationChannel.IN_APP);
        }
        return List.copyOf(uniqueChannels);
    }

    private boolean hasManualContent(CreateNotificationRequest request) {
        return request.getTitle() != null && !request.getTitle().isBlank()
                && request.getMessage() != null && !request.getMessage().isBlank();
    }

    private void validateRequesterContext(CreateNotificationRequest request, Long requestUserId) {
        if (requestUserId == null) {
            return;
        }

        if (!requestUserId.equals(request.getRecipientId())) {
            throw new BusinessException(NotificationErrorCode.FORBIDDEN_RECIPIENT);
        }

        if (request.getActorId() != null && !requestUserId.equals(request.getActorId())) {
            throw new BusinessException(NotificationErrorCode.FORBIDDEN_ACTOR);
        }
    }

    private Long resolveActorId(CreateNotificationRequest request, Long requestUserId) {
        if (requestUserId != null) {
            return requestUserId;
        }
        return request.getActorId();
    }

    private String safeOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
