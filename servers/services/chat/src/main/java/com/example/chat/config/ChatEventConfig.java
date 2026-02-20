package com.example.chat.config;

import com.example.event.DomainEvent;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "app.outbox", name = "enabled", havingValue = "false")
public class ChatEventConfig {

    @Bean
    public EventPublisher noopEventPublisher() {
        return new EventPublisher() {
            @Override
            public void publish(DomainEvent event) {
                // no-op
            }

            @Override
            public void publish(DomainEvent event, EventMetadata metadata) {
                // no-op
            }
        };
    }
}
