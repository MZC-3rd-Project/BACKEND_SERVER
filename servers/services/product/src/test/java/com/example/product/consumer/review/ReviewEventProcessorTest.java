package com.example.product.consumer.review;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.product.service.command.ItemReviewMetricCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewEventProcessorTest {

    @Mock
    private ItemReviewMetricCommandService itemReviewMetricCommandService;

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    private ReviewEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ReviewEventProcessor(itemReviewMetricCommandService, idempotentConsumerService);
    }

    @Test
    void process_reviewCreated_updatesItemMetrics() {
        String message = """
                {"eventId":"evt-review-1","eventType":"REVIEW_CREATED","itemId":101,"averageRating":4.50,"reviewCount":2}
                """;
        when(idempotentConsumerService.executeIdempotent(eq("evt-review-1"), eq("PRODUCT_REVIEW_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    supplier.get();
                    return Optional.empty();
                });

        processor.process(message, "evt-review-1", "REVIEW_CREATED");

        verify(itemReviewMetricCommandService).updateReviewMetrics(101L, new java.math.BigDecimal("4.50"), 2L);
    }

    @Test
    void process_invalidEnvelope_skipsProcessing() {
        String message = """
                {"eventType":"REVIEW_CREATED","itemId":101,"averageRating":4.50,"reviewCount":2}
                """;

        processor.process(message, null, "REVIEW_CREATED");

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(itemReviewMetricCommandService);
    }

    @Test
    void process_ignoresUnsupportedType() {
        String message = """
                {"eventId":"evt-review-2","eventType":"REVIEW_UPDATED","itemId":101,"averageRating":4.50,"reviewCount":2}
                """;

        processor.process(message, "evt-review-2", "REVIEW_UPDATED");

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(itemReviewMetricCommandService);
    }
}
