package com.example.clients.order.exception;

public class OrderClientValidationException extends OrderClientException {

    public OrderClientValidationException(String message) {
        super(message);
    }

    public OrderClientValidationException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}
