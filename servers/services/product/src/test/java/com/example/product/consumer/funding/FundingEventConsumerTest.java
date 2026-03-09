package com.example.product.consumer.funding;

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
class FundingEventConsumerTest {

    @Mock
    private InboxEnqueueService inboxEnqueueService;

    @Mock
    private FundingEventProcessor fundingEventProcessor;

    private EventConsumerRoutingProperties routingProperties;
    private FundingEventConsumer fundingEventConsumer;

    @BeforeEach
    void setUp() {
        routingProperties = new EventConsumerRoutingProperties();
        fundingEventConsumer = new FundingEventConsumer(
                new InboxRoutingSupport(inboxEnqueueService, new EventConsumerRoutingResolver(routingProperties)),
                fundingEventProcessor
        );
    }

    @Test
    void consume_directMode_dispatchesToProcessor() {
        configureDirectMode();
        String message = """
                {"eventId":"evt-1","eventType":"FUNDING_SUCCEEDED","itemId":101}
                """;
        when(fundingEventProcessor.supports("FUNDING_SUCCEEDED")).thenReturn(true);

        fundingEventConsumer.consume(recordOf(message));

        verify(fundingEventProcessor).process(message, "evt-1", "FUNDING_SUCCEEDED");
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_inboxMode_enqueuesMessage() {
        String message = """
                {"eventId":"evt-2","eventType":"FUNDING_FAILED","itemId":101}
                """;
        when(fundingEventProcessor.supports("FUNDING_FAILED")).thenReturn(true);
        when(inboxEnqueueService.enqueue(
                FundingEventProcessor.CONSUMER_NAME,
                "evt-2",
                "FUNDING_FAILED",
                message
        )).thenReturn(true);

        fundingEventConsumer.consume(recordOf(message));

        verify(inboxEnqueueService).enqueue(
                FundingEventProcessor.CONSUMER_NAME,
                "evt-2",
                "FUNDING_FAILED",
                message
        );
        verify(fundingEventProcessor, never()).process(message, "evt-2", "FUNDING_FAILED");
    }

    @Test
    void consume_skipsUnsupportedType() {
        configureDirectMode();
        String message = """
                {"eventId":"evt-3","eventType":"FUNDING_UNKNOWN","itemId":101}
                """;
        when(fundingEventProcessor.supports("FUNDING_UNKNOWN")).thenReturn(false);

        fundingEventConsumer.consume(recordOf(message));

        verify(fundingEventProcessor).supports("FUNDING_UNKNOWN");
        verify(fundingEventProcessor, never()).process(message, "evt-3", "FUNDING_UNKNOWN");
        verifyNoInteractions(inboxEnqueueService);
    }

    private ConsumerRecord<String, Object> recordOf(Object value) {
        return new ConsumerRecord<>("funding-events", 0, 0L, null, value);
    }

    private void configureDirectMode() {
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.DIRECT);
        routingProperties.getRouting().put(FundingEventProcessor.CONSUMER_NAME, routing);
    }
}
