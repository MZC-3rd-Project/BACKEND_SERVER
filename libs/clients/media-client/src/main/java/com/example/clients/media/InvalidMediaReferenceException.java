package com.example.clients.media;

public class InvalidMediaReferenceException extends MediaClientException {

    public InvalidMediaReferenceException(String message) {
        super(message);
    }

    public InvalidMediaReferenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
