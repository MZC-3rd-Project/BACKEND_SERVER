package com.example.stock.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.util.JsonUtils;
import com.example.stock.dto.request.InitializeStockRequest;
import com.example.stock.entity.StockItemType;
import com.example.stock.service.command.StockCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ItemEventConsumer {

    private final IdempotentConsumerService idempotentConsumerService;
    private final StockCommandService stockCommandService;

    @KafkaListener(topics = "item-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(String message) {
        try {
            ItemEventMessage event = JsonUtils.fromJson(message, ItemEventMessage.class);

            if (event.getEventId() == null || event.getEventType() == null) {
                log.error("[ItemConsumer] eventId 또는 eventType이 null입니다. message={}", message);
                return;
            }

            idempotentConsumerService.executeIdempotent(event.getEventId(), "ITEM_EVENT", () -> {
                switch (event.getEventType()) {
                    case "ITEM_CREATED" -> handleItemCreated(event);
                    default -> log.debug("처리하지 않는 이벤트 타입: {}", event.getEventType());
                }
                return null;
            });
        } catch (Exception e) {
            log.error("[ItemConsumer] 이벤트 처리 실패: {}", message, e);
            throw e;
        }
    }

    private void handleItemCreated(ItemEventMessage event) {
        log.info("[ItemConsumer] 아이템 생성 이벤트 수신: itemId={}, type={}", event.getItemId(), event.getItemType());

        List<ItemEventMessage.StockItemPayload> stockItems = event.getStockItems();
        if (stockItems == null || stockItems.isEmpty()) {
            log.info("[ItemConsumer] stockItems 없음 — 재고 초기화 건너뜀: itemId={}", event.getItemId());
            return;
        }

        for (ItemEventMessage.StockItemPayload si : stockItems) {
            try {
                StockItemType stockItemType = StockItemType.valueOf(si.getType());
                InitializeStockRequest request = InitializeStockRequest.of(
                        event.getItemId(), stockItemType, si.getReferenceId(), si.getTotalQuantity());
                stockCommandService.initializeStock(request);
                log.info("[ItemConsumer] 재고 자동 초기화 완료: itemId={}, type={}, refId={}, qty={}",
                        event.getItemId(), si.getType(), si.getReferenceId(), si.getTotalQuantity());
            } catch (Exception e) {
                log.error("[ItemConsumer] 재고 초기화 실패: itemId={}, type={}, refId={}",
                        event.getItemId(), si.getType(), si.getReferenceId(), e);
            }
        }
    }
}
