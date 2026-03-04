package com.example.clients.profile.dto;

public record ProfileCreateCommand(
        Long userId,
        String email,
        String nickname
) {
}
