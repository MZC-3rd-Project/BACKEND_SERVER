package com.example.search.consumer.item;

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
class ItemEventConsumerTest {

    @Mock
    private InboxEnqueueService inboxEnqueueService;

    @Mock
    private SearchItemEventProcessor searchItemEventProcessor;

    private EventConsumerRoutingProperties routingProperties;
    private ItemEventConsumer itemEventConsumer;

    @BeforeEach
    void setUp() {
        routingProperties = new EventConsumerRoutingProperties();
        itemEventConsumer = new ItemEventConsumer(
                new InboxRoutingSupport(inboxEnqueueService, new EventConsumerRoutingResolver(routingProperties)),
                searchItemEventProcessor
        );
    }

    @Test
    void consume_directMode_dispatchesToProcessor() {
        configureDirectMode();
        String message = """
                {"eventId":"evt-1","eventType":"ITEM_CREATED","itemId":101}
                """;
        when(searchItemEventProcessor.supports("ITEM_CREATED")).thenReturn(true);

        itemEventConsumer.consume(recordOf(message));

        verify(searchItemEventProcessor).process(message, "evt-1", "ITEM_CREATED");
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_inboxMode_enqueuesMessage() {
        String message = """
                {"eventId":"evt-2","eventType":"ITEM_CREATED","itemId":101}
                """;
        when(searchItemEventProcessor.supports("ITEM_CREATED")).thenReturn(true);
        when(inboxEnqueueService.enqueue(
                SearchItemEventProcessor.CONSUMER_NAME,
                "evt-2",
                "ITEM_CREATED",
                message
        )).thenReturn(true);

        itemEventConsumer.consume(recordOf(message));

        verify(inboxEnqueueService).enqueue(
                SearchItemEventProcessor.CONSUMER_NAME,
                "evt-2",
                "ITEM_CREATED",
                message
        );
        verify(searchItemEventProcessor, never()).process(message, "evt-2", "ITEM_CREATED");
    }

    @Test
    void consume_skipsUnsupportedType() {
        configureDirectMode();
        String message = """
                {"eventId":"evt-3","eventType":"ITEM_UPDATED","itemId":101}
                """;
        when(searchItemEventProcessor.supports("ITEM_UPDATED")).thenReturn(false);

        itemEventConsumer.consume(recordOf(message));

        verify(searchItemEventProcessor).supports("ITEM_UPDATED");
        verify(searchItemEventProcessor, never()).process(message, "evt-3", "ITEM_UPDATED");
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_skipsInvalidEnvelope() {
        String message = """
                {"eventType":"ITEM_CREATED","itemId":101}
                """;

        itemEventConsumer.consume(recordOf(message));

        verifyNoInteractions(searchItemEventProcessor);
        verifyNoInteractions(inboxEnqueueService);
    }

    private ConsumerRecord<String, Object> recordOf(Object value) {
        return new ConsumerRecord<>("item-events", 0, 0L, null, value);
    }

    private void configureDirectMode() {
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.DIRECT);
        routingProperties.getRouting().put(SearchItemEventProcessor.CONSUMER_NAME, routing);
    }
}
