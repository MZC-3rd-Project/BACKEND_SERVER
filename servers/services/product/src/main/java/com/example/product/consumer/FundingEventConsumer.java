package com.example.product.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.exception.BusinessException;
import com.example.core.util.JsonUtils;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.entity.item.ItemStatusHistory;
import com.example.product.event.ItemStatusChangedEvent;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.ItemStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FundingEventConsumer {

    private final ItemRepository itemRepository;
    private final ItemStatusHistoryRepository statusHistoryRepository;
    private final EventPublisher eventPublisher;
    private final IdempotentConsumerService idempotentConsumerService;

    @KafkaListener(topics = "funding-events", groupId = "product-service-group")
    @Transactional
    public void consume(String message) {
        try {
            FundingEventMessage event = JsonUtils.fromJson(message, FundingEventMessage.class);

            if (event.getEventId() == null || event.getEventType() == null) {
                log.error("[FundingConsumer] eventId 또는 eventType이 null입니다. message={}", message);
                return;
            }

            idempotentConsumerService.executeIdempotent(event.getEventId(), "FUNDING_EVENT", () -> {
                switch (event.getEventType()) {
                    case "FUNDING_COMPLETED" -> handleFundingCompleted(event);
                    case "FUNDING_FAILED" -> handleFundingFailed(event);
                    default -> log.warn("[FundingConsumer] Unknown event type: {}", event.getEventType());
                }
                return null;
            });
        } catch (Exception e) {
            log.error("[FundingConsumer] 이벤트 처리 실패. message={}", message, e);
            throw e;
        }
    }

    private void handleFundingCompleted(FundingEventMessage event) {
        if (event.getItemId() == null) {
            log.error("[FundingConsumer] FUNDING_COMPLETED: itemId가 null입니다");
            return;
        }
        changeItemStatus(event.getItemId(), ItemStatus.FUNDED, "펀딩 성공");
        log.info("[FundingConsumer] FUNDING_COMPLETED -> FUNDED for item #{}", event.getItemId());
    }

    private void handleFundingFailed(FundingEventMessage event) {
        if (event.getItemId() == null) {
            log.error("[FundingConsumer] FUNDING_FAILED: itemId가 null입니다");
            return;
        }
        changeItemStatus(event.getItemId(), ItemStatus.FUND_FAILED, "펀딩 실패");
        log.info("[FundingConsumer] FUNDING_FAILED -> FUND_FAILED for item #{}", event.getItemId());
    }

    private void changeItemStatus(Long itemId, ItemStatus newStatus, String reason) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));

        ItemStatus previousStatus = item.getStatus();
        item.changeStatus(newStatus);

        statusHistoryRepository.save(
                ItemStatusHistory.create(itemId, previousStatus, newStatus, reason, null));

        eventPublisher.publish(
                new ItemStatusChangedEvent(itemId, previousStatus.name(), newStatus.name()),
                EventMetadata.of("Item", String.valueOf(itemId)));
    }
}
