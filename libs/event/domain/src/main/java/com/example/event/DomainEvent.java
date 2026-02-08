package com.example.event;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
public abstract class DomainEvent {

    private final String eventId;
    private final String topic;
    private final String eventType;
    private final LocalDateTime occurredAt;

    protected DomainEvent(String topic) {
        this.eventId = UUID.randomUUID().toString();
        this.topic = topic;
        this.eventType = getEventTypeName();
        this.occurredAt = LocalDateTime.now();
    }

    protected DomainEvent(String eventId, String topic) {
        this.eventId = eventId;
        this.topic = topic;
        this.eventType = getEventTypeName();
        this.occurredAt = LocalDateTime.now();
    }

    public abstract String getEventTypeName();

    public abstract Map<String, Object> getPayload();
}
