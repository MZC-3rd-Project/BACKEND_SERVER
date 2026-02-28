package com.example.profile.client.exception;

public class ProfileClientException extends RuntimeException {
    public ProfileClientException(String message) {
        super(message);
    }
    public ProfileClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
