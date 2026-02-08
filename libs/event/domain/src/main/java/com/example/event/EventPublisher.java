package com.example.event;

public interface EventPublisher {

    void publish(DomainEvent event);

    void publish(DomainEvent event, EventMetadata metadata);
}
