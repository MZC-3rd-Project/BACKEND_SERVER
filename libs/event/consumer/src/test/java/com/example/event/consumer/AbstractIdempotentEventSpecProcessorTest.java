package com.example.event.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractIdempotentEventSpecProcessorTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private Consumer<SampleEvent> action;

    private SampleEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SampleEventProcessor(idempotentConsumerService, action);
    }

    @Test
    void process_runsConfiguredAction() {
        String message = """
                {"eventId":"evt-1","eventType":"SampleCreated","name":"sample"}
                """;

        when(idempotentConsumerService.executeIdempotent(eq("evt-1"), eq("SAMPLE_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    supplier.get();
                    return Optional.empty();
                });

        processor.process(message, "evt-1", "SampleCreated");

        verify(action).accept(new SampleEvent("evt-1", "SampleCreated", "sample"));
    }

    @Test
    void process_skipsInvalidPayload() {
        String message = """
                {"eventId":"evt-2","eventType":"SampleCreated","name":""}
                """;

        processor.process(message, "evt-2", "SampleCreated");

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(action);
    }

    @Test
    void process_ignoresUnsupportedType() {
        String message = """
                {"eventId":"evt-3","eventType":"UnknownType","name":"sample"}
                """;

        processor.process(message, "evt-3", "UnknownType");

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(action);
    }

    @Test
    void process_skipsMalformedPayload() {
        processor.process("{bad-json", "evt-4", "SampleCreated");

        verifyNoInteractions(idempotentConsumerService);
        verify(action, never()).accept(any());
    }

    private static final class SampleEventProcessor extends AbstractIdempotentEventSpecProcessor {

        private final Map<String, EventSpec<? extends EventEnvelope>> eventSpecs;

        private SampleEventProcessor(
                IdempotentConsumerService idempotentConsumerService,
                Consumer<SampleEvent> action
        ) {
            super(idempotentConsumerService);
            this.eventSpecs = Map.of(
                    "SampleCreated",
                    EventSpec.of(
                            SampleEvent.class,
                            event -> event.name() != null && !event.name().isBlank(),
                            action
                    )
            );
        }

        @Override
        protected Map<String, EventSpec<? extends EventEnvelope>> eventSpecs() {
            return eventSpecs;
        }

        @Override
        protected String idempotentEventType() {
            return "SAMPLE_EVENT";
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
}
