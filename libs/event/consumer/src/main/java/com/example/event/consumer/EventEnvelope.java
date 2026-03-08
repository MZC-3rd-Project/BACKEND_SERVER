package com.example.event.consumer;

public interface EventEnvelope {

    String getEventId();

    String getEventType();
}
