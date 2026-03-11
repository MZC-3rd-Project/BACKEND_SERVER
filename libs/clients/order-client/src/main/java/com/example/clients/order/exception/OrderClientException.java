package com.example.clients.order.exception;

public class OrderClientException extends RuntimeException {

    private final String errorCode;

    public OrderClientException(String message) {
        this(message, null, null);
    }

    public OrderClientException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public OrderClientException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
