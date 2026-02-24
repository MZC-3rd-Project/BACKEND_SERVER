package com.example.mediaworker.service;

public record MediaDerivativeFailureHandleResult(
        MediaDerivativeFailureOutcome outcome,
        MediaDerivativeFailureCode failureCode,
        Integer retryCount
) {
    public static MediaDerivativeFailureHandleResult skipped() {
        return new MediaDerivativeFailureHandleResult(
                MediaDerivativeFailureOutcome.SKIPPED,
                null,
                null
        );
    }

    public static MediaDerivativeFailureHandleResult retryScheduled(MediaDerivativeFailureCode failureCode, Integer retryCount) {
        return new MediaDerivativeFailureHandleResult(
                MediaDerivativeFailureOutcome.RETRY_SCHEDULED,
                failureCode,
                retryCount
        );
    }

    public static MediaDerivativeFailureHandleResult dlqFailed(MediaDerivativeFailureCode failureCode, Integer retryCount) {
        return new MediaDerivativeFailureHandleResult(
                MediaDerivativeFailureOutcome.DLQ_FAILED,
                failureCode,
                retryCount
        );
    }
}
