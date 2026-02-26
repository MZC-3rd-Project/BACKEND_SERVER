package com.example.clients.auth.exception;

public class AuthClientException extends RuntimeException {

    public AuthClientException(String message) {
        super(message);
    }

    public AuthClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
