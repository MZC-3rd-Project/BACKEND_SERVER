package com.example.auth.dto.response;

public record VerifyEmailResponse(
        boolean verified,
        String email
) {
    public static VerifyEmailResponse of(boolean verified, String email) {
        return new VerifyEmailResponse(verified, email);
    }
}
