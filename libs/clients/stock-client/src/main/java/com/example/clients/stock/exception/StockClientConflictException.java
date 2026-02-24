package com.example.clients.stock.exception;

public class StockClientConflictException extends StockClientException {

    public StockClientConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
