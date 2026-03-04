package com.example.profile.consumer;

public record UserEmailChangedPayload(
        Long userId,
        String oldEmail,
        String newEmail
) implements UserProjectionPayload {
}
