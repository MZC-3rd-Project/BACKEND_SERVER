package com.example.profile.consumer.user.dto;

import com.example.event.consumer.EventEnvelope;

public record UserWithdrawnEventDto(
        String eventId,
        String eventType,
        Long userId
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
