package com.example.profile.consumer;

public record UserWithdrawnPayload(
        Long userId
) implements UserProjectionPayload {
}
