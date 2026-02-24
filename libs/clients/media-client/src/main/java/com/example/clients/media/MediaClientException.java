package com.example.clients.media;

public class MediaClientException extends RuntimeException {

    public MediaClientException(String message) {
        super(message);
    }

    public MediaClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
