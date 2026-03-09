package com.example.event.inbox;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.ConsumerRoutingMode;
import com.example.event.consumer.EventConsumerRoutingProperties;
import com.example.event.consumer.EventConsumerRoutingResolver;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.RouteSpec;
import com.example.event.consumer.RoutedEventConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
class AbstractInboxAwareRoutingJsonMessageConsumerTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private InboxEnqueueService inboxEnqueueService;

    @Mock
    private SampleAction sampleAction;

    private EventConsumerRoutingProperties routingProperties;
    private SampleConsumer consumer;

    @BeforeEach
    void setUp() {
        routingProperties = new EventConsumerRoutingProperties();
        consumer = new SampleConsumer(
                idempotentConsumerService,
                new InboxRoutingSupport(inboxEnqueueService, new EventConsumerRoutingResolver(routingProperties)),
                sampleAction
        );
    }

    @Test
    void consumeRoutedRecord_enqueuesWhenInboxMode() {
        when(inboxEnqueueService.enqueue("sample-consumer", "evt-1", "SAMPLE_CREATED", SAMPLE_CREATED_MESSAGE))
                .thenReturn(true);

        consumer.consume(recordOf(SAMPLE_CREATED_MESSAGE));

        verify(inboxEnqueueService).enqueue("sample-consumer", "evt-1", "SAMPLE_CREATED", SAMPLE_CREATED_MESSAGE);
        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(sampleAction);
    }

    @Test
    void consumeRoutedRecord_processesDirectWhenConfiguredOverrideExists() {
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.DIRECT);
        routingProperties.getRouting().put("sample-consumer", routing);
        when(idempotentConsumerService.executeIdempotent(eq("evt-1"), eq("SAMPLE_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    supplier.get();
                    return Optional.empty();
                });

        consumer.consume(recordOf(SAMPLE_CREATED_MESSAGE));

        verify(sampleAction).accept("sample");
        verify(inboxEnqueueService, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    void consumeRoutedRecord_skipsUnsupportedTypeBeforeRouting() {
        String message = """
                {"eventId":"evt-2","eventType":"UNKNOWN","name":"sample"}
                """;

        consumer.consume(recordOf(message));

        verifyNoInteractions(inboxEnqueueService);
        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(sampleAction);
    }

    private ConsumerRecord<String, Object> recordOf(Object value) {
        return new ConsumerRecord<>("sample-events", 0, 0L, null, value);
    }

    @RoutedEventConsumer(consumerName = "sample-consumer", defaultMode = ConsumerRoutingMode.INBOX)
    @InboxConsumerBinding(consumerName = "sample-consumer")
    private static final class SampleConsumer extends AbstractInboxAwareRoutingJsonMessageConsumer<SampleEvent> {

        private final Map<String, RouteSpec<SampleEvent>> routeSpecs;

        private SampleConsumer(
                IdempotentConsumerService idempotentConsumerService,
                InboxRoutingSupport inboxRoutingSupport,
                SampleAction sampleAction
        ) {
            super(idempotentConsumerService, inboxRoutingSupport);
            this.routeSpecs = Map.of(
                    "SAMPLE_CREATED",
                    RouteSpec.of(
                            event -> event.name() != null && !event.name().isBlank(),
                            event -> sampleAction.accept(event.name())
                    )
            );
        }

        private void consume(ConsumerRecord<String, Object> record) {
            consumeRoutedRecord(record);
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

    private static final String SAMPLE_CREATED_MESSAGE = """
            {"eventId":"evt-1","eventType":"SAMPLE_CREATED","name":"sample"}
            """;
}
