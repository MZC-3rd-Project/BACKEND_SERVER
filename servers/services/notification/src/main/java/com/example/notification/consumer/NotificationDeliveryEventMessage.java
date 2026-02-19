package com.example.notification.consumer;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class NotificationDeliveryEventMessage {

    private String eventId;
    private String eventType;
    private Long notificationId;
    private String channel;
    private String emailTo;
}
