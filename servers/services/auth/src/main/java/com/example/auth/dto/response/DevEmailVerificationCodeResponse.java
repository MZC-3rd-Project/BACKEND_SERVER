package com.example.auth.dto.response;

import com.example.auth.entity.EmailVerification;

import java.time.LocalDateTime;

public record DevEmailVerificationCodeResponse(
        String email,
        String code,
        boolean expired,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {
    public static DevEmailVerificationCodeResponse from(EmailVerification verification) {
        return new DevEmailVerificationCodeResponse(
                verification.getEmail(),
                verification.getCode(),
                verification.isExpired(),
                verification.getExpiresAt(),
                verification.getCreatedAt()
        );
    }
}
