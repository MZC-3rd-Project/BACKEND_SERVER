package com.example.storequery.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.EventMessageProcessor;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.storequery.service.StoreReadModelProjectionApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@InboxConsumerBinding(consumerName = StoreQueryProjectionEventProcessor.CONSUMER_NAME)
public class StoreQueryProjectionEventProcessor implements EventMessageProcessor {

    public static final String CONSUMER_NAME = "store-query-projection-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "STORE_QUERY_PROJECTION_EVENT";
    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
        "StoreCreated",
        "StoreUpdated",
        "StoreDeleted",
        "ITEM_CREATED",
        "ITEM_UPDATED",
        "ITEM_STATUS_CHANGED",
        "ITEM_DELETED",
        "UserCreated",
        "UserEmailChanged",
        "UserWithdrawn",
        "ProfileCreated",
        "ProfileUpdated"
    );

    private final IdempotentConsumerService idempotentConsumerService;
    private final StoreReadModelProjectionApplicationService projectionApplicationService;

    @Override
    public boolean supports(String eventType) {
        return StringUtils.hasText(eventType) && SUPPORTED_EVENT_TYPES.contains(eventType);
    }

    @Override
    public void process(String message, String eventId, String eventType) {
        if (!StringUtils.hasText(eventId) || !supports(eventType)) {
            log.warn("[StoreQueryProjectionEventProcessor] skip invalid event. eventId={}, eventType={}", eventId, eventType);
            return;
        }

        try {
            idempotentConsumerService.executeIdempotent(eventId, IDEMPOTENT_EVENT_TYPE, () -> {
                projectionApplicationService.projectByTrigger(eventType, null, message);
                log.info("[StoreQueryProjectionEventProcessor] projected event. eventId={}, eventType={}", eventId, eventType);
                return null;
            });
        } catch (Exception exception) {
            log.error("[StoreQueryProjectionEventProcessor] projection failed. eventId={}, eventType={}",
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
