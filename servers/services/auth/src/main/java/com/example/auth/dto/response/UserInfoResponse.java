package com.example.auth.dto.response;

import com.example.auth.entity.User;

public record UserInfoResponse(
        Long userId,
        String keycloakId,
        String email,
        String nickname,
        String role,
        String status
) {
    public static UserInfoResponse from(User user) {
        return new UserInfoResponse(
                user.getId(),
                user.getKeycloakId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole().name(),
                user.getStatus().name()
        );
    }
}
