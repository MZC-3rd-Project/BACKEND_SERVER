package com.example.mediaworker.service;

public enum MediaDerivativeFailureOutcome {
    RETRY_SCHEDULED,
    DLQ_FAILED,
    SKIPPED
}

