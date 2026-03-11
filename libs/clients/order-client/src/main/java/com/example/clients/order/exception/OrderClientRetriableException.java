package com.example.clients.order.exception;

public class OrderClientRetriableException extends OrderClientException {

    public OrderClientRetriableException(String message, Throwable cause) {
        super(message, null, cause);
    }

    public OrderClientRetriableException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}
