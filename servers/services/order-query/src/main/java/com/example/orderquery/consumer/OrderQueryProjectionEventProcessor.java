package com.example.orderquery.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.EventMessageProcessor;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.orderquery.service.OrderDetailProjectionApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@InboxConsumerBinding(consumerName = OrderQueryProjectionEventProcessor.CONSUMER_NAME)
public class OrderQueryProjectionEventProcessor implements EventMessageProcessor {

    public static final String CONSUMER_NAME = "order-query-projection-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "ORDER_QUERY_PROJECTION_EVENT";

    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            "ORDER_CREATED_EVENT",
            "ORDER_PAID_EVENT",
            "ORDER_CANCELLED_EVENT",
            "ORDER_REFUND_REQUESTED_EVENT",
            "ORDER_REFUNDED_EVENT",
            "ITEM_UPDATED",
            "StoreUpdated"
    );

    private final IdempotentConsumerService idempotentConsumerService;
    private final OrderDetailProjectionApplicationService projectionApplicationService;

    @Override
    public boolean supports(String eventType) {
        return StringUtils.hasText(eventType) && SUPPORTED_EVENT_TYPES.contains(eventType);
    }

    @Override
    public void process(String message, String eventId, String eventType) {
        if (!StringUtils.hasText(eventId) || !supports(eventType)) {
            log.warn("[OrderQueryProjectionEventProcessor] skip invalid event. eventId={}, eventType={}", eventId, eventType);
            return;
        }

        try {
            idempotentConsumerService.executeIdempotent(eventId, IDEMPOTENT_EVENT_TYPE, () -> {
                projectionApplicationService.project(eventType, message);
                log.info("[OrderQueryProjectionEventProcessor] projected event. eventId={}, eventType={}", eventId, eventType);
                return null;
            });
        } catch (Exception exception) {
            log.error("[OrderQueryProjectionEventProcessor] projection failed. eventId={}, eventType={}",
                    eventId, eventType, exception);
            throw propagate(exception);
        }
    }

    private RuntimeException propagate(Exception exception) {
        if (exception instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(exception);
    }
}
