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
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

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

            if (event.getEventId() == null || event.getEventType() == null || event.getItemId() == null) {
                log.error("[ItemConsumer] eventId/eventType/itemId 중 null 값이 존재합니다. message={}", message);
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
                if (!isValidStockPayload(event.getItemId(), si)) {
                    continue;
                }
                StockItemType stockItemType = parseStockItemType(si.getType(), event.getItemId(), si.getReferenceId());
                if (stockItemType == null) {
                    continue;
                }
                InitializeStockRequest request = InitializeStockRequest.of(
                        event.getItemId(), stockItemType, si.getReferenceId(), si.getTotalQuantity());
                stockCommandService.initializeStock(request);
                log.info("[ItemConsumer] 재고 자동 초기화 완료: itemId={}, type={}, refId={}, qty={}",
                        event.getItemId(), si.getType(), si.getReferenceId(), si.getTotalQuantity());
            } catch (Exception e) {
                log.error("[ItemConsumer] 재고 초기화 실패(메시지 재처리를 위해 예외 전파): itemId={}, type={}, refId={}",
                        event.getItemId(), si.getType(), si.getReferenceId(), e);
                throw e;
            }
        }
    }

    private boolean isValidStockPayload(Long itemId, ItemEventMessage.StockItemPayload payload) {
        if (payload == null) {
            log.error("[ItemConsumer] stockItem payload가 null입니다. itemId={}", itemId);
            return false;
        }
        if (!StringUtils.hasText(payload.getType())) {
            log.error("[ItemConsumer] stockItem.type 누락. itemId={}, refId={}", itemId, payload.getReferenceId());
            return false;
        }
        if (payload.getTotalQuantity() < 0) {
            log.error("[ItemConsumer] stockItem.totalQuantity 음수. itemId={}, type={}, qty={}",
                    itemId, payload.getType(), payload.getTotalQuantity());
            return false;
        }
        return true;
    }

    private StockItemType parseStockItemType(String rawType, Long itemId, Long referenceId) {
        try {
            return StockItemType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.error("[ItemConsumer] 지원하지 않는 stockItemType. itemId={}, type={}, refId={}",
                    itemId, rawType, referenceId);
            return null;
        }
    }
}
