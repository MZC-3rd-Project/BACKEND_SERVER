package com.example.event.outbox;

import jakarta.annotation.PostConstruct;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class DomainEventPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private static ApplicationEventPublisher staticPublisher;

    public DomainEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void init() {
        DomainEventPublisher.staticPublisher = this.eventPublisher;
    }

    public static void publish(Object event) {
        if (staticPublisher != null) {
            staticPublisher.publishEvent(event);
        }
    }
}
