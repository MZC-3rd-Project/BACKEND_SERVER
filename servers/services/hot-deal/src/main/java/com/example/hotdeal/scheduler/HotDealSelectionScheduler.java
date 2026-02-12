package com.example.hotdeal.scheduler;

import com.example.hotdeal.client.ProductClient;
import com.example.hotdeal.client.StockClient;
import com.example.hotdeal.entity.HotDealStatus;
import com.example.hotdeal.repository.HotDealRepository;
import com.example.hotdeal.service.HotDealCommandService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HotDealSelectionScheduler {

    private final ProductClient productClient;
    private final StockClient stockClient;
    private final HotDealRepository hotDealRepository;
    private final HotDealCommandService hotDealCommandService;

    /**
     * 매일 자정에 실행: D-3 이내 마감 예정 상품 중 잔여율 조건에 맞는 상품을 핫딜로 전환
     */
    @Scheduled(cron = "0 0 0 * * *")
    @SchedulerLock(name = "hotDealSelection", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    public void selectHotDeals() {
        log.info("Hot deal selection scheduler started");

        try {
            JsonNode items = productClient.findItemsEndingSoon(3);

            if (items == null || !items.isArray()) {
                log.info("No items ending soon found");
                return;
            }

            int selectedCount = 0;

            for (JsonNode item : items) {
                Long itemId = item.path("itemId").asLong();
                String title = item.path("title").asText();
                Long price = item.path("price").asLong();
                Long stockItemId = item.path("stockItemId").asLong();

                // 이미 핫딜로 등록된 상품인지 확인
                boolean exists = hotDealRepository.existsByItemIdAndStatusIn(
                        itemId, List.of(HotDealStatus.SCHEDULED, HotDealStatus.ACTIVE));
                if (exists) {
                    continue;
                }

                // 재고 정보 조회
                JsonNode stockInfo = stockClient.getStockInfo(stockItemId);
                int totalQuantity = stockInfo.path("totalQuantity").asInt();
                int availableQuantity = stockInfo.path("availableQuantity").asInt();

                if (totalQuantity == 0) {
                    continue;
                }

                double remainingRate = (double) availableQuantity / totalQuantity * 100;

                // 잔여율 20% 미만이면 스킵 (이미 잘 팔리는 상품)
                if (remainingRate < 20) {
                    continue;
                }

                // 할인율 계산
                int discountRate = calculateDiscountRate(remainingRate);

                // 핫딜 생성 및 활성화
                hotDealCommandService.createAndActivate(
                        itemId, title, price, discountRate, availableQuantity,
                        LocalDateTime.now(), LocalDateTime.now().plusDays(3),
                        "자동 선정 — 잔여율 " + String.format("%.1f", remainingRate) + "%");

                selectedCount++;
                log.info("Hot deal selected: itemId={}, title={}, remainingRate={:.1f}%, discountRate={}%",
                        itemId, title, remainingRate, discountRate);
            }

            log.info("Hot deal selection completed: {} deals created", selectedCount);
        } catch (Exception e) {
            log.error("Hot deal selection scheduler failed", e);
        }
    }

    /**
     * 잔여율 기반 할인율 계산
     * - 50% 이상 → 10%
     * - 30~50% → 20%
     * - 20~30% → 30%
     * - 20% 미만 → 40%
     */
    private int calculateDiscountRate(double remainingRate) {
        if (remainingRate >= 50) {
            return 10;
        } else if (remainingRate >= 30) {
            return 20;
        } else if (remainingRate >= 20) {
            return 30;
        } else {
            return 40;
        }
    }
}
