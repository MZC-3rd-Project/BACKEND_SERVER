package com.example.analyticsdashboard.consumer.support;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;

import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class AbstractAnalyticsEventProcessor<T extends EventEnvelope>
        extends AbstractIdempotentEventSpecProcessor {

    private final Class<T> eventClass;

    protected AbstractAnalyticsEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            Class<T> eventClass
    ) {
        super(idempotentConsumerService);
        this.eventClass = eventClass;
    }

    protected final EventSpec<T> eventSpec(Predicate<T> validator, Consumer<T> action) {
        return EventSpec.of(eventClass, validator, action);
    }
}
