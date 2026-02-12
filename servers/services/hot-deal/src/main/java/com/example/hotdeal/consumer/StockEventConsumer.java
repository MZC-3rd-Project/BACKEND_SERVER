package com.example.hotdeal.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.hotdeal.client.ProductClient;
import com.example.hotdeal.client.StockClient;
import com.example.hotdeal.entity.HotDealStatus;
import com.example.hotdeal.repository.HotDealRepository;
import com.example.hotdeal.service.HotDealCommandService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockEventConsumer {

    private final IdempotentConsumerService idempotentConsumerService;
    private final HotDealRepository hotDealRepository;
    private final HotDealCommandService hotDealCommandService;
    private final ProductClient productClient;
    private final StockClient stockClient;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "stock-events", groupId = "hot-deal-service-group")
    public void consume(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventType = event.path("eventType").asText();
            String eventId = event.path("eventId").asText();

            if (!"STOCK_THRESHOLD".equals(eventType)) {
                return;
            }

            idempotentConsumerService.executeIdempotent(eventId, "STOCK_EVENT", () -> {
                handleStockThreshold(event.path("payload"));
                return null;
            });
        } catch (Exception e) {
            log.error("Stock event consumption failed", e);
        }
    }

    private void handleStockThreshold(JsonNode payload) {
        Long itemId = payload.path("itemId").asLong();
        Long stockItemId = payload.path("stockItemId").asLong();
        int totalQuantity = payload.path("totalQuantity").asInt();
        int availableQuantity = payload.path("availableQuantity").asInt();

        if (totalQuantity == 0) {
            return;
        }

        // 이미 핫딜로 등록된 상품인지 확인
        boolean exists = hotDealRepository.existsByItemIdAndStatusIn(
                itemId, List.of(HotDealStatus.SCHEDULED, HotDealStatus.ACTIVE));
        if (exists) {
            return;
        }

        double remainingRate = (double) availableQuantity / totalQuantity * 100;

        // 잔여율 20% 미만이면 스킵
        if (remainingRate < 20) {
            return;
        }

        // 상품 정보 조회
        JsonNode itemData = productClient.findItem(itemId);
        String title = itemData.path("title").asText();
        Long price = itemData.path("price").asLong();

        // 할인율 계산
        int discountRate;
        if (remainingRate >= 50) {
            discountRate = 10;
        } else if (remainingRate >= 30) {
            discountRate = 20;
        } else {
            discountRate = 30;
        }

        hotDealCommandService.createAndActivate(
                itemId, title, price, discountRate, availableQuantity,
                LocalDateTime.now(), LocalDateTime.now().plusDays(1),
                "재고 임계값 이벤트 — 잔여율 " + String.format("%.1f", remainingRate) + "%");

        log.info("Hot deal created from stock threshold: itemId={}, remainingRate={:.1f}%",
                itemId, remainingRate);
    }
}
