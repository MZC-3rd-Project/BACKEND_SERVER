package com.example.clients.media.exception;

public class InvalidMediaReferenceException extends MediaClientException {

    public InvalidMediaReferenceException(String message) {
        super(message);
    }

    public InvalidMediaReferenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
