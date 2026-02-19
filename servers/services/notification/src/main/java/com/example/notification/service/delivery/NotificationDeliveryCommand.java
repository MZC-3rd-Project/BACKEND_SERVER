package com.example.notification.service.delivery;

import com.example.notification.entity.NotificationType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationDeliveryCommand {

    private Long userId;
    private NotificationType type;
    private String title;
    private String message;
    private String emailTo;
}
