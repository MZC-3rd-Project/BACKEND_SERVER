package com.example.chat.consumer;

import com.example.chat.service.command.ChatFundingSyncService;
import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentRoutingJsonMessageConsumer;
import com.example.event.consumer.RouteSpec;
import com.example.event.inbox.InboxConsumerBinding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = ChatFundingEventProcessor.CONSUMER_NAME)
public class ChatFundingEventProcessor extends AbstractIdempotentRoutingJsonMessageConsumer<FundingEventMessage> {

    public static final String CONSUMER_NAME = "chat-funding-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "FUNDING_EVENT";

    private final ChatFundingSyncService chatFundingSyncService;
    private final Map<String, RouteSpec<FundingEventMessage>> routeSpecs;

    public ChatFundingEventProcessor(
            ChatFundingSyncService chatFundingSyncService,
            IdempotentConsumerService idempotentConsumerService
    ) {
        super(idempotentConsumerService);
        this.chatFundingSyncService = chatFundingSyncService;
        this.routeSpecs = Map.of(
                "FUNDING_CREATED", RouteSpec.of(this::handleFundingCreated),
                "FUNDING_PARTICIPATED", RouteSpec.of(this::handleFundingParticipated),
                "FUNDING_REFUNDED", RouteSpec.of(this::handleFundingRefunded),
                "FUNDING_SUCCEEDED", RouteSpec.of(this::handleFundingClosed),
                "FUNDING_FAILED", RouteSpec.of(this::handleFundingClosed)
        );
    }

    @Override
    protected Class<FundingEventMessage> payloadType() {
        return FundingEventMessage.class;
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected void onMissingEnvelope(String message, FundingEventMessage event) {
        log.error("[ChatFundingConsumer] eventId/eventType is null. message={}", message);
    }

    @Override
    protected void onProcessingException(String message, FundingEventMessage event, Exception exception) {
        log.error("[ChatFundingConsumer] failed to process funding event. message={}", message, exception);
        throw propagate(exception);
    }

    @Override
    protected Map<String, RouteSpec<FundingEventMessage>> routeSpecs() {
        return routeSpecs;
    }

    private void handleFundingCreated(FundingEventMessage event) {
        chatFundingSyncService.syncFundingCreated(event);
    }

    private void handleFundingParticipated(FundingEventMessage event) {
        chatFundingSyncService.syncFundingParticipated(event);
    }

    private void handleFundingRefunded(FundingEventMessage event) {
        chatFundingSyncService.syncFundingRefunded(event);
    }

    private void handleFundingClosed(FundingEventMessage event) {
        chatFundingSyncService.syncFundingClosed(event);
    }
}
