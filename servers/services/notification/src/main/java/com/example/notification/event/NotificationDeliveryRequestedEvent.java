package com.example.notification.event;

import com.example.event.DomainEvent;
import com.example.notification.entity.NotificationChannel;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class NotificationDeliveryRequestedEvent extends DomainEvent {

    private static final String TOPIC = "notification-delivery-events";
    private static final String EVENT_TYPE = "NOTIFICATION_DELIVERY_REQUESTED";

    private final Long notificationId;
    private final String channel;
    private final String emailTo;

    public NotificationDeliveryRequestedEvent(Long notificationId, NotificationChannel channel, String emailTo) {
        super(TOPIC);
        this.notificationId = notificationId;
        this.channel = channel.name();
        this.emailTo = emailTo;
    }

    @Override
    public String getEventTypeName() {
        return EVENT_TYPE;
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationId", notificationId);
        payload.put("channel", channel);
        payload.put("emailTo", emailTo);
        return payload;
    }
}
