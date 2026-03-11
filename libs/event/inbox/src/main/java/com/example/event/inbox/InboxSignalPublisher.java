package com.example.event.inbox;

@FunctionalInterface
public interface InboxSignalPublisher {

    void signal(String consumerName);
}
