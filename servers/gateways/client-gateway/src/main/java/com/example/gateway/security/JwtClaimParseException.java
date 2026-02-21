package com.example.gateway.security;

public class JwtClaimParseException extends RuntimeException {

    public JwtClaimParseException(String message) {
        super(message);
    }

    public JwtClaimParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
