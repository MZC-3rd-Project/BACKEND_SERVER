package com.example.gateway.security.session.domain;

public record SessionValidationResult(boolean allowed, String code, String message) {

    public static SessionValidationResult allow() {
        return new SessionValidationResult(true, null, null);
    }

    public static SessionValidationResult deny(String code, String message) {
        return new SessionValidationResult(false, code, message);
    }
}
