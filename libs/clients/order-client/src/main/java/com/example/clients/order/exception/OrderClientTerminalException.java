package com.example.clients.order.exception;

public class OrderClientTerminalException extends OrderClientException {

    public OrderClientTerminalException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}
