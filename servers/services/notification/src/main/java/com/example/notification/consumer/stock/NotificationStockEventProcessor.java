package com.example.notification.consumer.stock;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.notification.consumer.support.NotificationDispatchSupport;
import com.example.notification.entity.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = NotificationStockEventProcessor.CONSUMER_NAME)
public class NotificationStockEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "notification-stock-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "STOCK_EVENT";

    private final NotificationDispatchSupport notificationDispatchSupport;
    private final Map<String, EventSpec<StockEventMessage>> eventSpecs;

    public NotificationStockEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            NotificationDispatchSupport notificationDispatchSupport
    ) {
        super(idempotentConsumerService);
        this.notificationDispatchSupport = notificationDispatchSupport;
        this.eventSpecs = Map.of(
                "STOCK_DEPLETED", EventSpec.of(StockEventMessage.class, event -> true, this::handleStockDepleted)
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected Map<String, EventSpec<StockEventMessage>> eventSpecs() {
        return eventSpecs;
    }

    @Override
    protected void onInvalidEnvelope(String eventId, String eventType, String message) {
        log.warn("Skip invalid stock-events message. eventId={}, eventType={}", eventId, eventType);
    }

    private void handleStockDepleted(StockEventMessage event) {
        NotificationDispatchSupport.ResolvedRecipient resolved = notificationDispatchSupport.resolveRecipient(
                event.getItemId(),
                event.getSellerId(),
                event.getTitle()
        );
        if (resolved.recipientId() == null) {
            log.warn("Skip STOCK_DEPLETED notification. recipient unresolved. itemId={}", event.getItemId());
            return;
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("stockItemId", event.getStockItemId());
        variables.put("itemId", event.getItemId());
        variables.put("title", resolved.title());
        variables.put("quantity", event.getQuantity());
        variables.put("remainingQuantity", event.getRemainingQuantity());
        variables.put("totalQuantity", event.getTotalQuantity());

        notificationDispatchSupport.dispatchNotification(
                resolved.recipientId(),
                NotificationType.STOCK_DEPLETED,
                "ITEM",
                event.getItemId(),
                event.getEventId(),
                "재고가 모두 소진되었습니다",
                notificationDispatchSupport.safeValue(resolved.title()) + " 상품의 재고가 모두 소진되었습니다.",
                variables
        );
    }
}
