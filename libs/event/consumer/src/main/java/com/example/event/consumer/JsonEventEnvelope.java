package com.example.event.consumer;

public record JsonEventEnvelope(
        String eventId,
        String eventType
) implements EventEnvelope {

    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public String getEventType() {
        return eventType;
    }
}
