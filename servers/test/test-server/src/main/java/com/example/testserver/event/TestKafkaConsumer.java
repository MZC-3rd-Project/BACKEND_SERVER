package com.example.testserver.event;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestKafkaConsumer {

    private final IdempotentConsumerService idempotentConsumerService;

    /**
     * [공통모듈 사용법] Kafka Consumer + 멱등성 보장
     *
     * 1. @KafkaListener로 토픽 구독
     * 2. IdempotentConsumerService.executeIdempotent()로 중복 처리 방지
     * 3. 같은 eventId 메시지가 재전달되어도 한 번만 처리
     */
    @KafkaListener(topics = "test-item-events", groupId = "test-server-group")
    public void consume(String message) {
        log.info("[Kafka Consumer] Raw message received: {}", message);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = JsonUtils.fromJson(message, Map.class);
            String eventId = String.valueOf(payload.getOrDefault("eventId", message.hashCode()));

            idempotentConsumerService.executeIdempotent(
                    eventId,
                    "TEST_ITEM_CREATED",
                    () -> {
                        log.info("[Kafka Consumer] Processing event: {}", payload);
                        return null;
                    }
            );
        } catch (Exception e) {
            log.warn("[Kafka Consumer] Failed to process message: {}", e.getMessage());
        }
    }
}
