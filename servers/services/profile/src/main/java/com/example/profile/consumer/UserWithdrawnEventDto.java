package com.example.profile.consumer;

public record UserWithdrawnEventDto(
        String eventId,
        String eventType,
        Long userId
) {
}
