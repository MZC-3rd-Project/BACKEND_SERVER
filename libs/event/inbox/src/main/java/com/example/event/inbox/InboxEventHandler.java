package com.example.event.inbox;

public interface InboxEventHandler {

    String consumerName();

    boolean supports(String eventType);

    void handle(String eventId, String eventType, String payload) throws Exception;
}
