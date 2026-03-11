package com.example.profile.dto.request;

public record InternalProfileCreateRequest(
        Long userId,
        String email,
        String nickname
) {
}
