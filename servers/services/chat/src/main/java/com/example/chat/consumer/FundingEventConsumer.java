package com.example.chat.consumer;

import com.example.chat.service.command.ChatFundingSyncService;
import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FundingEventConsumer {

    private final ChatFundingSyncService chatFundingSyncService;
    private final IdempotentConsumerService idempotentConsumerService;

    @KafkaListener(topics = "funding-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(String message) {
        try {
            FundingEventMessage event = JsonUtils.fromJson(message, FundingEventMessage.class);

            if (event.getEventId() == null || event.getEventType() == null) {
                log.error("[ChatFundingConsumer] eventId/eventType is null. message={}", message);
                return;
            }

            idempotentConsumerService.executeIdempotent(event.getEventId(), "FUNDING_EVENT", () -> {
                switch (event.getEventType()) {
                    case "FUNDING_CREATED" -> chatFundingSyncService.syncFundingCreated(event);
                    case "FUNDING_PARTICIPATED" -> chatFundingSyncService.syncFundingParticipated(event);
                    case "FUNDING_REFUNDED" -> chatFundingSyncService.syncFundingRefunded(event);
                    case "FUNDING_SUCCEEDED", "FUNDING_FAILED" -> chatFundingSyncService.syncFundingClosed(event);
                    default -> log.debug("Ignore funding event type: {}", event.getEventType());
                }
                return null;
            });
        } catch (Exception e) {
            log.error("[ChatFundingConsumer] failed to process funding event. message={}", message, e);
            throw e;
        }
    }
}
