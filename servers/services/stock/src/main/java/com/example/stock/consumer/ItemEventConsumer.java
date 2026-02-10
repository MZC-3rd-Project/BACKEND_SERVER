package com.example.stock.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ItemEventConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "item-events", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message) {
        try {
            ItemEventMessage event = objectMapper.readValue(message, ItemEventMessage.class);
            log.info("아이템 이벤트 수신: type={}, itemId={}", event.getEventType(), event.getItemId());

            switch (event.getEventType()) {
                case "ITEM_CREATED" -> handleItemCreated(event);
                default -> log.debug("처리하지 않는 이벤트 타입: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("아이템 이벤트 처리 실패: {}", message, e);
        }
    }

    private void handleItemCreated(ItemEventMessage event) {
        // 향후 재고 자동 초기화 로직 — Product에서 SeatGrade/ItemOption 정보와 함께
        // 별도 이벤트로 전달받아 자동 초기화할 예정
        log.info("아이템 생성 이벤트 수신 — 재고 초기화 대기: itemId={}, type={}", event.getItemId(), event.getItemType());
    }
}
