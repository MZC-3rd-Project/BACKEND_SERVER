package com.example.gateway.security;

public class SessionClaimParseException extends RuntimeException {

    public SessionClaimParseException(String message) {
        super(message);
    }

    public SessionClaimParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
