package com.example.notification.service.query;

import java.time.LocalDateTime;
import java.util.List;

public record NotificationHistoryView(
        Long id,
        String type,
        String channel,
        String title,
        String message,
        String referenceType,
        String referenceId,
        boolean read,
        LocalDateTime readAt,
        LocalDateTime createdAt,
        List<NotificationDeliveryView> deliveries
) {

    public record NotificationDeliveryView(
            String channel,
            String status,
            String provider,
            int attemptCount,
            String lastErrorCode,
            String lastErrorMessage,
            LocalDateTime nextRetryAt,
            LocalDateTime deliveredAt
    ) {
    }
}
