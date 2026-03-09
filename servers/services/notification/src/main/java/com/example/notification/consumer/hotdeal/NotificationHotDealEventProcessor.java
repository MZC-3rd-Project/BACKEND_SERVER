package com.example.notification.consumer.hotdeal;

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
@InboxConsumerBinding(consumerName = NotificationHotDealEventProcessor.CONSUMER_NAME)
public class NotificationHotDealEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "notification-hotdeal-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "HOTDEAL_EVENT";

    private final NotificationDispatchSupport notificationDispatchSupport;
    private final Map<String, EventSpec<HotDealEventMessage>> eventSpecs;

    public NotificationHotDealEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            NotificationDispatchSupport notificationDispatchSupport
    ) {
        super(idempotentConsumerService);
        this.notificationDispatchSupport = notificationDispatchSupport;
        this.eventSpecs = Map.of(
                "HOT_DEAL_STARTED", EventSpec.of(HotDealEventMessage.class, event -> true, this::handleHotDealStarted)
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected Map<String, EventSpec<HotDealEventMessage>> eventSpecs() {
        return eventSpecs;
    }

    @Override
    protected void onInvalidEnvelope(String eventId, String eventType, String message) {
        log.warn("Skip invalid hotdeal-events message. eventId={}, eventType={}", eventId, eventType);
    }

    private void handleHotDealStarted(HotDealEventMessage event) {
        NotificationDispatchSupport.ResolvedRecipient resolved = notificationDispatchSupport.resolveRecipient(
                event.getItemId(),
                event.getSellerId(),
                event.getTitle()
        );
        if (resolved.recipientId() == null) {
            log.warn("Skip HOT_DEAL_STARTED notification. recipient unresolved. itemId={}", event.getItemId());
            return;
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("hotDealId", event.getHotDealId());
        variables.put("itemId", event.getItemId());
        variables.put("title", resolved.title());
        variables.put("discountedPrice", event.getDiscountedPrice());
        variables.put("discountRate", event.getDiscountRate());
        variables.put("maxQuantity", event.getMaxQuantity());

        notificationDispatchSupport.dispatchNotification(
                resolved.recipientId(),
                NotificationType.HOTDEAL,
                "HOT_DEAL",
                event.getHotDealId(),
                event.getEventId(),
                "핫딜이 시작되었습니다",
                notificationDispatchSupport.safeValue(resolved.title()) + " 상품의 핫딜이 시작되었습니다.",
                variables
        );
    }
}
