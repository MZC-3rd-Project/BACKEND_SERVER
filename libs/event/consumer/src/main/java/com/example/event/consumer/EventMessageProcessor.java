package com.example.event.consumer;

public interface EventMessageProcessor {

    boolean supports(String eventType);

    void process(String message, String eventId, String eventType);
}
