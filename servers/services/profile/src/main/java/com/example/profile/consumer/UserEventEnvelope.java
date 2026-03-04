package com.example.profile.consumer;

public record UserEventEnvelope(
        String eventId,
        String eventType
) {
}
