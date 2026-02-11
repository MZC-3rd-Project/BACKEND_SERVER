package com.example.stock.consumer;

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
public class ItemEventConsumer {

    private final IdempotentConsumerService idempotentConsumerService;

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
        // 향후 재고 자동 초기화 로직 — Product에서 SeatGrade/ItemOption 정보와 함께
        // 별도 이벤트로 전달받아 자동 초기화할 예정
        log.info("[ItemConsumer] 아이템 생성 이벤트 수신 — 재고 초기화 대기: itemId={}, type={}", event.getItemId(), event.getItemType());
    }
}
