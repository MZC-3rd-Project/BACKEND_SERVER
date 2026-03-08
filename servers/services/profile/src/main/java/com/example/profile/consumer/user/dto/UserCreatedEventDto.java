package com.example.profile.consumer.user.dto;

import com.example.event.consumer.EventEnvelope;

public record UserCreatedEventDto(
        String eventId,
        String eventType,
        Long userId,
        String email,
        String nickname
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
