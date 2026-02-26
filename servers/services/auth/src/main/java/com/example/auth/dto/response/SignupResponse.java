package com.example.auth.dto.response;

public record SignupResponse(
        Long userId,
        String email
) {
    public static SignupResponse of(Long userId, String email) {
        return new SignupResponse(userId, email);
    }
}
