package com.example.product.consumer.review;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.product.service.command.ItemReviewMetricCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = ReviewEventProcessor.CONSUMER_NAME)
public class ReviewEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "product-review-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "PRODUCT_REVIEW_EVENT";

    private final ItemReviewMetricCommandService itemReviewMetricCommandService;
    private final Map<String, EventSpec<ReviewEventMessage>> eventSpecs;

    public ReviewEventProcessor(
            ItemReviewMetricCommandService itemReviewMetricCommandService,
            IdempotentConsumerService idempotentConsumerService
    ) {
        super(idempotentConsumerService);
        this.itemReviewMetricCommandService = itemReviewMetricCommandService;
        this.eventSpecs = Map.of(
                "REVIEW_CREATED", EventSpec.of(ReviewEventMessage.class, this::hasMetrics, this::handleReviewCreated)
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected Map<String, EventSpec<ReviewEventMessage>> eventSpecs() {
        return eventSpecs;
    }

    @Override
    protected void onInvalidEnvelope(String eventId, String eventType, String message) {
        log.error("[ReviewEventProcessor] eventId 또는 eventType이 null입니다. message={}", message);
    }

    @Override
    protected <T extends EventEnvelope> void onInvalidPayload(T event, String message, String eventId, String eventType) {
        log.warn("[ReviewEventProcessor] review metrics payload 누락으로 스킵. eventId={}, eventType={}", eventId, eventType);
    }

    @Override
    protected <T extends EventEnvelope> void onProcessingException(
            T event,
            String message,
            String eventId,
            String eventType,
            Exception exception
    ) {
        log.error("[ReviewEventProcessor] 이벤트 처리 실패. message={}", message, exception);
        throw propagate(exception);
    }

    private boolean hasMetrics(ReviewEventMessage event) {
        return event.getItemId() != null
                && event.getAverageRating() != null
                && event.getReviewCount() != null;
    }

    private void handleReviewCreated(ReviewEventMessage event) {
        itemReviewMetricCommandService.updateReviewMetrics(
                event.getItemId(),
                event.getAverageRating(),
                event.getReviewCount()
        );
    }
}
