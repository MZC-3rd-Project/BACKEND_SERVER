package com.example.sales.service.command;

import com.example.sales.exception.SalesErrorCode;

import java.time.Duration;

public record CheckoutSubmitFailureDecision(
        SalesErrorCode errorCode,
        boolean releaseReservation,
        boolean retryable,
        Duration retryDelay
) {

    public static CheckoutSubmitFailureDecision cancelImmediately(SalesErrorCode errorCode) {
        return new CheckoutSubmitFailureDecision(errorCode, true, false, null);
    }

    public static CheckoutSubmitFailureDecision retryLater(SalesErrorCode errorCode, Duration retryDelay) {
        return new CheckoutSubmitFailureDecision(errorCode, false, true, retryDelay);
    }
}
