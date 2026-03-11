package com.example.clients.order.exception;

public class OrderClientConflictException extends OrderClientTerminalException {

    public OrderClientConflictException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}
