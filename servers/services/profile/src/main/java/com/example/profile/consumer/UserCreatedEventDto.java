package com.example.profile.consumer;

public record UserCreatedEventDto(
        String eventId,
        String eventType,
        Long userId,
        String email,
        String nickname
) {
}
