package com.example.search.consumer.support;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.search.service.metrics.SearchMetricsService;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;
import java.util.function.Predicate;

@Slf4j
public abstract class AbstractSearchEventSpecProcessor<T extends EventEnvelope>
        extends AbstractIdempotentEventSpecProcessor {

    private final Class<T> eventClass;
    private final SearchMetricsService searchMetricsService;

    protected AbstractSearchEventSpecProcessor(
            IdempotentConsumerService idempotentConsumerService,
            SearchMetricsService searchMetricsService,
            Class<T> eventClass
    ) {
        super(idempotentConsumerService);
        this.searchMetricsService = searchMetricsService;
        this.eventClass = eventClass;
    }

    protected final EventSpec<T> eventSpec(Predicate<T> validator, Consumer<T> action) {
        return EventSpec.of(eventClass, validator, action);
    }

    protected final EventSpec<T> eventSpec(Consumer<T> action) {
        return EventSpec.of(eventClass, action);
    }

    @Override
    protected String processorLogName() {
        return "[" + getClass().getSimpleName() + "]";
    }

    @Override
    protected void onInvalidEnvelope(String eventId, String eventType, String message) {
        log.error("{} eventId/eventType 누락. message={}", processorLogName(), message);
    }

    @Override
    protected <E extends EventEnvelope> void onProcessed(E event, String eventId, String eventType) {
        searchMetricsService.recordIndexingEvent(eventType, true);
    }

    @Override
    protected <E extends EventEnvelope> void onProcessingException(
            E event,
            String message,
            String eventId,
            String eventType,
            Exception exception
    ) {
        searchMetricsService.recordIndexingEvent(event == null ? "UNKNOWN" : event.getEventType(), false);
        recordFailure(eventClass.cast(event), message, exception);
        log.error("{} 이벤트 처리 실패. message={}", processorLogName(), message, exception);
        throw propagate(exception);
    }

    protected abstract void recordFailure(T event, String message, Exception exception);
}
