package com.example.event.outbox;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DomainEventPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private static volatile ApplicationEventPublisher staticPublisher;

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
        } else {
            log.warn("ApplicationEventPublisher가 초기화되지 않았습니다. 이벤트가 발행되지 않습니다: {}", event);
        }
    }
}
