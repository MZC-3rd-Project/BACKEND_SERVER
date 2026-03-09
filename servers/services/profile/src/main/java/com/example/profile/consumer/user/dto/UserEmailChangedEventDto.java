package com.example.profile.consumer.user.dto;

import com.example.event.consumer.EventEnvelope;

public record UserEmailChangedEventDto(
        String eventId,
        String eventType,
        Long userId,
        String oldEmail,
        String newEmail
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
