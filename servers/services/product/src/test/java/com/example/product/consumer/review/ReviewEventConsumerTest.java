package com.example.product.consumer.review;

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
class ReviewEventConsumerTest {

    @Mock
    private InboxEnqueueService inboxEnqueueService;

    @Mock
    private ReviewEventProcessor reviewEventProcessor;

    private EventConsumerRoutingProperties routingProperties;
    private ReviewEventConsumer reviewEventConsumer;

    @BeforeEach
    void setUp() {
        routingProperties = new EventConsumerRoutingProperties();
        reviewEventConsumer = new ReviewEventConsumer(
                new InboxRoutingSupport(inboxEnqueueService, new EventConsumerRoutingResolver(routingProperties)),
                reviewEventProcessor
        );
    }

    @Test
    void consume_directMode_dispatchesToProcessor() {
        configureDirectMode();
        String message = """
                {"eventId":"evt-review-1","eventType":"REVIEW_CREATED","itemId":101,"averageRating":4.50,"reviewCount":2}
                """;
        when(reviewEventProcessor.supports("REVIEW_CREATED")).thenReturn(true);

        reviewEventConsumer.consume(recordOf(message));

        verify(reviewEventProcessor).process(message, "evt-review-1", "REVIEW_CREATED");
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_inboxMode_enqueuesMessage() {
        String message = """
                {"eventId":"evt-review-2","eventType":"REVIEW_CREATED","itemId":101,"averageRating":4.50,"reviewCount":2}
                """;
        when(reviewEventProcessor.supports("REVIEW_CREATED")).thenReturn(true);
        when(inboxEnqueueService.enqueue(
                ReviewEventProcessor.CONSUMER_NAME,
                "evt-review-2",
                "REVIEW_CREATED",
                message
        )).thenReturn(true);

        reviewEventConsumer.consume(recordOf(message));

        verify(inboxEnqueueService).enqueue(
                ReviewEventProcessor.CONSUMER_NAME,
                "evt-review-2",
                "REVIEW_CREATED",
                message
        );
        verify(reviewEventProcessor, never()).process(message, "evt-review-2", "REVIEW_CREATED");
    }

    @Test
    void consume_skipsUnsupportedType() {
        configureDirectMode();
        String message = """
                {"eventId":"evt-review-3","eventType":"REVIEW_UPDATED","itemId":101,"averageRating":4.50,"reviewCount":2}
                """;
        when(reviewEventProcessor.supports("REVIEW_UPDATED")).thenReturn(false);

        reviewEventConsumer.consume(recordOf(message));

        verify(reviewEventProcessor).supports("REVIEW_UPDATED");
        verify(reviewEventProcessor, never()).process(message, "evt-review-3", "REVIEW_UPDATED");
        verifyNoInteractions(inboxEnqueueService);
    }

    private ConsumerRecord<String, Object> recordOf(Object value) {
        return new ConsumerRecord<>("review-events", 0, 0L, null, value);
    }

    private void configureDirectMode() {
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.DIRECT);
        routingProperties.getRouting().put(ReviewEventProcessor.CONSUMER_NAME, routing);
    }
}
