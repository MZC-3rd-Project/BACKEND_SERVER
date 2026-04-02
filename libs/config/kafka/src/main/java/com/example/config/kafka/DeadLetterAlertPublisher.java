package com.example.config.kafka;

import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;

public class DeadLetterAlertPublisher {

    private static final String UNKNOWN_SERVICE = "unknown-service";

    private final ObjectProvider<EventPublisher> eventPublisherProvider;
    private final Environment environment;

    public DeadLetterAlertPublisher(
            ObjectProvider<EventPublisher> eventPublisherProvider,
            Environment environment
    ) {
        this.eventPublisherProvider = eventPublisherProvider;
        this.environment = environment;
    }

    public boolean publish(DeadLetterMessage deadLetterMessage, DlqAlertType alertType) {
        EventPublisher eventPublisher = eventPublisherProvider.getIfAvailable();
        if (eventPublisher == null || deadLetterMessage == null) {
            return false;
        }

        String serviceName = environment.getProperty("spring.application.name", UNKNOWN_SERVICE);
        eventPublisher.publish(
                new DlqAlertEvent(serviceName, alertType, deadLetterMessage),
                EventMetadata.of("DeadLetterMessage", String.valueOf(deadLetterMessage.getId()))
        );
        return true;
    }
}
