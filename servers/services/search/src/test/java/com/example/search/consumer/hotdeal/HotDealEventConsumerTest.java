package com.example.search.consumer.hotdeal;

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
class HotDealEventConsumerTest {

    @Mock
    private InboxEnqueueService inboxEnqueueService;

    @Mock
    private SearchHotDealEventProcessor searchHotDealEventProcessor;

    private EventConsumerRoutingProperties routingProperties;
    private HotDealEventConsumer hotDealEventConsumer;

    @BeforeEach
    void setUp() {
        routingProperties = new EventConsumerRoutingProperties();
        hotDealEventConsumer = new HotDealEventConsumer(
                new InboxRoutingSupport(inboxEnqueueService, new EventConsumerRoutingResolver(routingProperties)),
                searchHotDealEventProcessor
        );
    }

    @Test
    void consume_directMode_dispatchesToProcessor() {
        configureDirectMode();
        String message = """
                {"eventId":"evt-1","eventType":"HOT_DEAL_STARTED","itemId":101}
                """;
        when(searchHotDealEventProcessor.supports("HOT_DEAL_STARTED")).thenReturn(true);

        hotDealEventConsumer.consume(recordOf(message));

        verify(searchHotDealEventProcessor).process(message, "evt-1", "HOT_DEAL_STARTED");
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_inboxMode_enqueuesMessage() {
        String message = """
                {"eventId":"evt-2","eventType":"HOT_DEAL_ENDED","itemId":101}
                """;
        when(searchHotDealEventProcessor.supports("HOT_DEAL_ENDED")).thenReturn(true);
        when(inboxEnqueueService.enqueue(
                SearchHotDealEventProcessor.CONSUMER_NAME,
                "evt-2",
                "HOT_DEAL_ENDED",
                message
        )).thenReturn(true);

        hotDealEventConsumer.consume(recordOf(message));

        verify(inboxEnqueueService).enqueue(
                SearchHotDealEventProcessor.CONSUMER_NAME,
                "evt-2",
                "HOT_DEAL_ENDED",
                message
        );
        verify(searchHotDealEventProcessor, never()).process(message, "evt-2", "HOT_DEAL_ENDED");
    }

    @Test
    void consume_skipsUnsupportedType() {
        configureDirectMode();
        String message = """
                {"eventId":"evt-3","eventType":"HOT_DEAL_UNKNOWN","itemId":101}
                """;
        when(searchHotDealEventProcessor.supports("HOT_DEAL_UNKNOWN")).thenReturn(false);

        hotDealEventConsumer.consume(recordOf(message));

        verify(searchHotDealEventProcessor).supports("HOT_DEAL_UNKNOWN");
        verify(searchHotDealEventProcessor, never()).process(message, "evt-3", "HOT_DEAL_UNKNOWN");
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_skipsInvalidEnvelope() {
        String message = """
                {"eventType":"HOT_DEAL_STARTED","itemId":101}
                """;

        hotDealEventConsumer.consume(recordOf(message));

        verifyNoInteractions(searchHotDealEventProcessor);
        verifyNoInteractions(inboxEnqueueService);
    }

    private ConsumerRecord<String, Object> recordOf(Object value) {
        return new ConsumerRecord<>("hotdeal-events", 0, 0L, null, value);
    }

    private void configureDirectMode() {
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.DIRECT);
        routingProperties.getRouting().put(SearchHotDealEventProcessor.CONSUMER_NAME, routing);
    }
}
