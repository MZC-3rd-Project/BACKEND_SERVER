package com.example.event.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractIdempotentRoutingJsonMessageConsumerTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private SampleAction sampleAction;

    private SampleConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SampleConsumer(idempotentConsumerService, sampleAction);
    }

    @Test
    void consume_routesSupportedEvent() {
        when(idempotentConsumerService.executeIdempotent(eq("evt-1"), eq("SAMPLE_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    supplier.get();
                    return Optional.empty();
                });

        consumer.consume("""
                {"eventId":"evt-1","eventType":"sample_created","name":"sample"}
                """);

        verify(sampleAction).accept("sample");
    }

    @Test
    void consume_skipsInvalidPayload() {
        consumer.consume("""
                {"eventId":"evt-2","eventType":"SAMPLE_CREATED","name":""}
                """);

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(sampleAction);
    }

    @Test
    void consume_ignoresUnsupportedType() {
        when(idempotentConsumerService.executeIdempotent(eq("evt-3"), eq("SAMPLE_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    supplier.get();
                    return Optional.empty();
                });

        consumer.consume("""
                {"eventId":"evt-3","eventType":"UNKNOWN_EVENT","name":"sample"}
                """);

        verify(idempotentConsumerService).executeIdempotent(eq("evt-3"), eq("SAMPLE_EVENT"), any());
        verify(sampleAction, never()).accept(any());
    }

    private static final class SampleConsumer extends AbstractIdempotentRoutingJsonMessageConsumer<SampleEvent> {

        private final Map<String, RouteSpec<SampleEvent>> routeSpecs;
        private final SampleAction sampleAction;

        private SampleConsumer(IdempotentConsumerService idempotentConsumerService, SampleAction sampleAction) {
            super(idempotentConsumerService);
            this.routeSpecs = Map.of(
                    "SAMPLE_CREATED",
                    RouteSpec.of(
                            event -> event.name() != null && !event.name().isBlank(),
                            event -> sampleAction.accept(event.name())
                    )
            );
            this.sampleAction = sampleAction;
        }

        private void consume(String message) {
            consumeMessage(message);
        }

        @Override
        protected Class<SampleEvent> payloadType() {
            return SampleEvent.class;
        }

        @Override
        protected String idempotentEventType() {
            return "SAMPLE_EVENT";
        }

        @Override
        protected Map<String, RouteSpec<SampleEvent>> routeSpecs() {
            return routeSpecs;
        }
    }

    private record SampleEvent(
            String eventId,
            String eventType,
            String name
    ) implements EventEnvelope {

        @Override
        public String getEventId() {
            return eventId;
        }

        @Override
        public String getEventType() {
            return eventType;
        }
    }

    @FunctionalInterface
    private interface SampleAction {
        void accept(String value);
    }
}
