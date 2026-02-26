package com.example.notification.dto.query.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationDelivery;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class NotificationHistoryItemResponse {

    @SnowflakeId
    private Long id;

    private String type;
    private String channel;
    private String title;
    private String message;
    private String referenceType;
    private String referenceId;
    private boolean read;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    @Builder.Default
    private List<NotificationDeliverySummaryResponse> deliveries = List.of();

    public static NotificationHistoryItemResponse from(Notification notification) {
        return from(notification, List.of());
    }

    public static NotificationHistoryItemResponse from(Notification notification,
                                                       List<NotificationDelivery> deliveries) {
        return NotificationHistoryItemResponse.builder()
                .id(notification.getId())
                .type(notification.getType().name())
                .channel(notification.getChannel().name())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .referenceType(notification.getReferenceType())
                .referenceId(notification.getReferenceId())
                .read(notification.isRead())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .deliveries(deliveries.stream()
                        .map(NotificationDeliverySummaryResponse::from)
                        .toList())
                .build();
    }
}
