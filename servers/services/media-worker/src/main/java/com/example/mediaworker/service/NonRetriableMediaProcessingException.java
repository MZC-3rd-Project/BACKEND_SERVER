package com.example.mediaworker.service;

public class NonRetriableMediaProcessingException extends RuntimeException {

    public NonRetriableMediaProcessingException(String message) {
        super(message);
    }

    public NonRetriableMediaProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
