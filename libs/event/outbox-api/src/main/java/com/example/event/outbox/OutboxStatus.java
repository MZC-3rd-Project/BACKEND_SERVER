package com.example.event.outbox;

public enum OutboxStatus {
    PENDING,
    SENDING,
    PUBLISHED,
    FAILED
}
