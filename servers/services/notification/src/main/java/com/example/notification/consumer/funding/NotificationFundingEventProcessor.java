package com.example.notification.consumer.funding;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventEnvelope;
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
@InboxConsumerBinding(consumerName = NotificationFundingEventProcessor.CONSUMER_NAME)
public class NotificationFundingEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "notification-funding-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "FUNDING_EVENT";

    private final NotificationDispatchSupport notificationDispatchSupport;
    private final Map<String, EventSpec<FundingEventMessage>> eventSpecs;

    public NotificationFundingEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            NotificationDispatchSupport notificationDispatchSupport
    ) {
        super(idempotentConsumerService);
        this.notificationDispatchSupport = notificationDispatchSupport;
        this.eventSpecs = Map.of(
                "FUNDING_SUCCEEDED", EventSpec.of(FundingEventMessage.class, this::hasSellerId, this::handleFundingSucceeded),
                "FUNDING_FAILED", EventSpec.of(FundingEventMessage.class, this::hasSellerId, this::handleFundingFailed)
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected Map<String, EventSpec<FundingEventMessage>> eventSpecs() {
        return eventSpecs;
    }

    @Override
    protected void onInvalidEnvelope(String eventId, String eventType, String message) {
        log.warn("Skip invalid funding-events message. eventId={}, eventType={}", eventId, eventType);
    }

    @Override
    protected <T extends EventEnvelope> void onInvalidPayload(T event, String message, String eventId, String eventType) {
        FundingEventMessage fundingEvent = (FundingEventMessage) event;
        log.warn("Skip {} notification. sellerId is null. campaignId={}",
                eventType, fundingEvent == null ? null : fundingEvent.getCampaignId());
    }

    private boolean hasSellerId(FundingEventMessage event) {
        return event.getSellerId() != null;
    }

    private void handleFundingSucceeded(FundingEventMessage event) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("campaignId", event.getCampaignId());
        variables.put("itemId", event.getItemId());
        variables.put("goalAmount", event.getGoalAmount());
        variables.put("currentAmount", event.getCurrentAmount());
        variables.put("currentQuantity", event.getCurrentQuantity());
        variables.put("fundingType", event.getFundingType());

        notificationDispatchSupport.dispatchNotification(
                event.getSellerId(),
                NotificationType.FUNDING_SUCCESS,
                "CAMPAIGN",
                event.getCampaignId(),
                event.getEventId(),
                "펀딩이 성공적으로 마감되었어요",
                "캠페인 #" + notificationDispatchSupport.safeValue(event.getCampaignId()) + "이(가) 목표를 달성했습니다.",
                variables
        );
    }

    private void handleFundingFailed(FundingEventMessage event) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("campaignId", event.getCampaignId());
        variables.put("itemId", event.getItemId());
        variables.put("goalAmount", event.getGoalAmount());
        variables.put("currentAmount", event.getCurrentAmount());
        variables.put("currentQuantity", event.getCurrentQuantity());
        variables.put("fundingType", event.getFundingType());

        notificationDispatchSupport.dispatchNotification(
                event.getSellerId(),
                NotificationType.FUNDING_FAIL,
                "CAMPAIGN",
                event.getCampaignId(),
                event.getEventId(),
                "펀딩이 목표를 달성하지 못했어요",
                "캠페인 #" + notificationDispatchSupport.safeValue(event.getCampaignId())
                        + "이(가) 마감되었지만 목표 달성에 실패했습니다.",
                variables
        );
    }
}
