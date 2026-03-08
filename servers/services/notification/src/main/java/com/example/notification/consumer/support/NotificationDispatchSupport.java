package com.example.notification.consumer.support;

import com.example.clients.product.dto.ProductItemSummary;
import com.example.clients.product.facade.ProductItemSummaryClientFacade;
import com.example.notification.dto.command.request.CreateNotificationRequest;
import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationType;
import com.example.notification.service.command.NotificationCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotificationDispatchSupport {

    private static final List<String> DEFAULT_CHANNELS = List.of(NotificationChannel.IN_APP.name());

    private final NotificationCommandService notificationCommandService;
    private final ProductItemSummaryClientFacade productClient;

    public void dispatchNotification(
            Long recipientId,
            NotificationType type,
            String referenceType,
            Object referenceId,
            String eventId,
            String title,
            String message,
            Map<String, Object> variables
    ) {
        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .recipientId(recipientId)
                .type(type.name())
                .channels(DEFAULT_CHANNELS)
                .referenceType(referenceType)
                .referenceId(referenceId == null ? null : String.valueOf(referenceId))
                .externalEventId(eventId)
                .title(title)
                .message(message)
                .variables(variables)
                .build();
        notificationCommandService.createAndSend(request, null);
    }

    public ResolvedRecipient resolveRecipient(Long itemId, Long explicitRecipientId, String explicitTitle) {
        Long recipientId = explicitRecipientId;
        String title = normalizeText(explicitTitle);

        if ((recipientId == null || title == null) && itemId != null) {
            ProductItemSummary itemSummary = productClient.findItemSummary(itemId);
            if (itemSummary != null) {
                if (recipientId == null) {
                    recipientId = itemSummary.sellerId();
                }
                if (title == null) {
                    title = normalizeText(itemSummary.title());
                }
            }
        }

        return new ResolvedRecipient(recipientId, title);
    }

    public String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    public String safeValue(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    public record ResolvedRecipient(Long recipientId, String title) {
    }
}
