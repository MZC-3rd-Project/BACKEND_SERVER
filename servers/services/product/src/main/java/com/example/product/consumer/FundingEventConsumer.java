package com.example.product.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.exception.BusinessException;
import com.example.core.util.JsonUtils;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.product.domain.item.Item;
import com.example.product.domain.item.ItemRepository;
import com.example.product.domain.item.ItemStatus;
import com.example.product.domain.item.ItemStatusHistory;
import com.example.product.domain.item.ItemStatusHistoryRepository;
import com.example.product.event.ItemStatusChangedEvent;
import com.example.product.exception.ProductErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

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
        Map<String, Object> event = JsonUtils.fromJson(message, Map.class);
        String eventId = (String) event.get("eventId");
        String eventType = (String) event.get("eventType");

        idempotentConsumerService.executeIdempotent(eventId, "FUNDING_EVENT", () -> {
            switch (eventType) {
                case "FUNDING_COMPLETED" -> handleFundingCompleted(event);
                case "FUNDING_FAILED" -> handleFundingFailed(event);
                default -> log.warn("[FundingConsumer] Unknown event type: {}", eventType);
            }
            return null;
        });
    }

    private void handleFundingCompleted(Map<String, Object> event) {
        Long itemId = toLong(event.get("itemId"));
        changeItemStatus(itemId, ItemStatus.FUNDED, "펀딩 성공");
        log.info("[FundingConsumer] FUNDING_COMPLETED -> FUNDED for item #{}", itemId);
    }

    private void handleFundingFailed(Map<String, Object> event) {
        Long itemId = toLong(event.get("itemId"));
        changeItemStatus(itemId, ItemStatus.FUND_FAILED, "펀딩 실패");
        log.info("[FundingConsumer] FUNDING_FAILED -> FUND_FAILED for item #{}", itemId);
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

    private Long toLong(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(String.valueOf(value));
    }
}
