package com.example.event.payment;

public enum PaymentEventType {

    PAYMENT_COMPLETED,
    PAYMENT_FAILED,
    PAYMENT_CANCELLED,
    PAYMENT_TIMED_OUT,
    PAYMENT_REFUNDED;

    public String value() {
        return name();
    }
}
