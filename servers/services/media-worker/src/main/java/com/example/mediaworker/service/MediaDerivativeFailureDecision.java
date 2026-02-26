package com.example.mediaworker.service;

public record MediaDerivativeFailureDecision(
        boolean retriable,
        MediaDerivativeFailureCode failureCode
) {
}
