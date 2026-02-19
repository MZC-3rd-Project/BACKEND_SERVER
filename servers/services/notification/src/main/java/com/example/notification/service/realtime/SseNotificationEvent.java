package com.example.notification.service.realtime;

import com.example.notification.entity.Notification;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class SseNotificationEvent {

    private Long userId;
    private Long notificationId;
    private String type;
    private String title;
    private String message;
    private String referenceType;
    private String referenceId;
    private String status;
    private LocalDateTime occurredAt;

    @Builder
    private SseNotificationEvent(Long userId,
                                 Long notificationId,
                                 String type,
                                 String title,
                                 String message,
                                 String referenceType,
                                 String referenceId,
                                 String status,
                                 LocalDateTime occurredAt) {
        this.userId = userId;
        this.notificationId = notificationId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.status = status;
        this.occurredAt = occurredAt;
    }

    public static SseNotificationEvent from(Notification notification) {
        return SseNotificationEvent.builder()
                .userId(notification.getRecipientId())
                .notificationId(notification.getId())
                .type(notification.getType().name())
                .title(sanitize(notification.getTitle()))
                .message(sanitize(notification.getMessage()))
                .referenceType(sanitize(notification.getReferenceType()))
                .referenceId(sanitize(notification.getReferenceId()))
                .status(notification.getStatus().name())
                .occurredAt(LocalDateTime.now())
                .build();
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return HtmlUtils.htmlEscape(value);
    }
}
