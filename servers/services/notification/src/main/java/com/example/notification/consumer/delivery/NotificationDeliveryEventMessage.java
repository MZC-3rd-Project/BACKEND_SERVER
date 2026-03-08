package com.example.notification.consumer.delivery;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class NotificationDeliveryEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;
    private Long notificationId;
    private String channel;
    private String emailTo;
}
