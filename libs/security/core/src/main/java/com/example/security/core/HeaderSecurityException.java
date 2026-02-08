package com.example.security.core;

public class HeaderSecurityException extends RuntimeException {

    public HeaderSecurityException(String message) {
        super(message);
    }

    public HeaderSecurityException(String message, Throwable cause) {
        super(message, cause);
    }
}
