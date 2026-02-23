package com.example.mediaworker.service;

public enum MediaDerivativeFailureCode {
    RETRIABLE_EXCEPTION,
    NON_RETRIABLE_EXCEPTION,
    MAX_RETRY_EXCEEDED
}
