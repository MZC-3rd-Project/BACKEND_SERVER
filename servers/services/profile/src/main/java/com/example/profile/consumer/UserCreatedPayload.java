package com.example.profile.consumer;

public record UserCreatedPayload(
        Long userId,
        String email,
        String nickname
) implements UserProjectionPayload {
}
