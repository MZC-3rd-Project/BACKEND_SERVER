package com.example.auth.config;

import com.example.event.DomainEvent;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "app.outbox", name = "enabled", havingValue = "false")
public class LocalEventPublisherConfig {

    @Bean
    public EventPublisher eventPublisher() {
        return new EventPublisher() {
            @Override
            public void publish(DomainEvent event) {
                log.info("[NO-OP] Event published: type={}, id={}",
                        event.getEventTypeName(), event.getEventId());
            }

            @Override
            public void publish(DomainEvent event, EventMetadata metadata) {
                log.info("[NO-OP] Event published: type={}, aggregate={}/{}",
                        event.getEventTypeName(), metadata.aggregateType(), metadata.aggregateId());
            }
        };
    }
}
