package com.example.profile.consumer;

public record UserEmailChangedEventDto(
        String eventId,
        String eventType,
        Long userId,
        String oldEmail,
        String newEmail
) {
}
