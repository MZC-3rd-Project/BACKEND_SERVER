package com.example.mediaworker.consumer;

import com.example.event.consumer.ConsumerRoutingMode;
import com.example.event.consumer.EventConsumerRoutingProperties;
import com.example.event.consumer.EventConsumerRoutingResolver;
import com.example.event.inbox.InboxEnqueueService;
import com.example.event.inbox.InboxRoutingSupport;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaConfirmedEventConsumerTest {

    @Mock
    private InboxEnqueueService inboxEnqueueService;

    @Mock
    private MediaConfirmedEventProcessor mediaConfirmedEventProcessor;

    private EventConsumerRoutingProperties routingProperties;
    private MediaConfirmedEventConsumer mediaConfirmedEventConsumer;

    @BeforeEach
    void setUp() {
        routingProperties = new EventConsumerRoutingProperties();
        mediaConfirmedEventConsumer = new MediaConfirmedEventConsumer(
                new InboxRoutingSupport(inboxEnqueueService, new EventConsumerRoutingResolver(routingProperties)),
                mediaConfirmedEventProcessor
        );
    }

    @Test
    void consume_directMode_dispatchesToProcessor() {
        configureDirectMode();
        String message = """
                {"eventId":"evt-1","eventType":"MEDIA_CONFIRMED","mediaId":101,"mediaVersion":2}
                """;
        when(mediaConfirmedEventProcessor.supports("MEDIA_CONFIRMED")).thenReturn(true);

        mediaConfirmedEventConsumer.consume(recordOf(message));

        verify(mediaConfirmedEventProcessor).process(message, "evt-1", "MEDIA_CONFIRMED");
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_inboxMode_enqueuesMessage() {
        String message = """
                {"eventId":"evt-2","eventType":"MEDIA_CONFIRMED","mediaId":202,"mediaVersion":3}
                """;
        when(mediaConfirmedEventProcessor.supports("MEDIA_CONFIRMED")).thenReturn(true);
        when(inboxEnqueueService.enqueue(
                MediaConfirmedEventProcessor.CONSUMER_NAME,
                "evt-2",
                "MEDIA_CONFIRMED",
                message
        )).thenReturn(true);

        mediaConfirmedEventConsumer.consume(recordOf(message));

        verify(inboxEnqueueService).enqueue(
                MediaConfirmedEventProcessor.CONSUMER_NAME,
                "evt-2",
                "MEDIA_CONFIRMED",
                message
        );
        verify(mediaConfirmedEventProcessor, never()).process(message, "evt-2", "MEDIA_CONFIRMED");
    }

    @Test
    void consume_skipsInvalidEnvelope() {
        String message = """
                {"eventType":"MEDIA_CONFIRMED","mediaId":101}
                """;

        mediaConfirmedEventConsumer.consume(recordOf(message));

        verifyNoInteractions(mediaConfirmedEventProcessor);
        verifyNoInteractions(inboxEnqueueService);
    }

    private ConsumerRecord<String, Object> recordOf(Object value) {
        return new ConsumerRecord<>("media.confirmed", 0, 0L, null, value);
    }

    private void configureDirectMode() {
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.DIRECT);
        routingProperties.getRouting().put(MediaConfirmedEventProcessor.CONSUMER_NAME, routing);
    }
}
