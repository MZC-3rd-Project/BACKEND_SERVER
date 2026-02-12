package com.example.hotdeal.service;

import com.example.core.exception.BusinessException;
import com.example.hotdeal.client.ProductClient;
import com.example.hotdeal.dto.CreateHotDealRequest;
import com.example.hotdeal.dto.HotDealDetailResponse;
import com.example.hotdeal.entity.HotDeal;
import com.example.hotdeal.entity.HotDealStatus;
import com.example.hotdeal.entity.HotDealStatusHistory;
import com.example.hotdeal.event.HotDealEndedEvent;
import com.example.hotdeal.event.HotDealStartedEvent;
import com.example.hotdeal.exception.HotDealErrorCode;
import com.example.hotdeal.repository.HotDealRepository;
import com.example.hotdeal.repository.HotDealStatusHistoryRepository;
import com.example.event.EventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class HotDealCommandService {

    private final HotDealRepository hotDealRepository;
    private final HotDealStatusHistoryRepository statusHistoryRepository;
    private final EventPublisher eventPublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductClient productClient;

    private static final String STOCK_KEY_PREFIX = "hotdeal:stock:";

    /**
     * 판매자가 직접 핫딜 생성
     */
    public HotDealDetailResponse createManual(CreateHotDealRequest request, Long userId) {
        // 상품 정보 조회 + 검증
        JsonNode itemData = productClient.findItem(request.getItemId());
        String title = itemData.path("title").asText();
        Long originalPrice = itemData.path("price").asLong();

        // 이미 핫딜로 등록된 상품인지 확인
        boolean exists = hotDealRepository.existsByItemIdAndStatusIn(
                request.getItemId(), List.of(HotDealStatus.SCHEDULED, HotDealStatus.ACTIVE));
        if (exists) {
            throw new BusinessException(HotDealErrorCode.HOT_DEAL_ALREADY_EXISTS);
        }

        LocalDateTime startAt = request.getStartAt() != null ? request.getStartAt() : LocalDateTime.now();
        LocalDateTime endAt = request.getEndAt() != null ? request.getEndAt() : startAt.plusHours(2);

        HotDeal hotDeal = createAndActivate(
                request.getItemId(), title, originalPrice,
                request.getDiscountRate(), request.getMaxQuantity(),
                startAt, endAt, "판매자 수동 생성 (userId=" + userId + ")");

        return HotDealDetailResponse.from(hotDeal);
    }

    public HotDeal createAndActivate(Long itemId, String title, Long originalPrice,
                                      Integer discountRate, Integer maxQuantity,
                                      LocalDateTime startAt, LocalDateTime endAt, String reason) {
        HotDeal hotDeal = HotDeal.create(itemId, title, originalPrice,
                discountRate, maxQuantity, startAt, endAt);
        hotDeal.activate();
        hotDealRepository.save(hotDeal);

        // 상태 이력
        statusHistoryRepository.save(
                HotDealStatusHistory.create(hotDeal.getId(), null, HotDealStatus.SCHEDULED, "핫딜 생성"));
        statusHistoryRepository.save(
                HotDealStatusHistory.create(hotDeal.getId(), HotDealStatus.SCHEDULED, HotDealStatus.ACTIVE, reason));

        // Redis에 재고 초기화
        redisTemplate.opsForValue().set(STOCK_KEY_PREFIX + hotDeal.getId(), maxQuantity);

        // 이벤트 발행
        eventPublisher.publish(new HotDealStartedEvent(
                hotDeal.getId(), itemId, title, hotDeal.getDiscountedPrice(),
                discountRate, maxQuantity, startAt, endAt));

        log.info("Hot deal activated: id={}, itemId={}, discountRate={}%", hotDeal.getId(), itemId, discountRate);

        return hotDeal;
    }

    public void endExpiredDeals() {
        List<HotDeal> expired = hotDealRepository.findExpiredActiveDeals(LocalDateTime.now());

        for (HotDeal hotDeal : expired) {
            HotDealStatus from = hotDeal.getStatus();
            hotDeal.end();

            statusHistoryRepository.save(
                    HotDealStatusHistory.create(hotDeal.getId(), from, HotDealStatus.ENDED, "시간 만료"));

            // Redis 재고 키 삭제
            redisTemplate.delete(STOCK_KEY_PREFIX + hotDeal.getId());

            eventPublisher.publish(new HotDealEndedEvent(
                    hotDeal.getId(), hotDeal.getItemId(),
                    hotDeal.getSoldQuantity(), hotDeal.getMaxQuantity()));

            log.info("Hot deal ended: id={}, sold={}/{}", hotDeal.getId(),
                    hotDeal.getSoldQuantity(), hotDeal.getMaxQuantity());
        }
    }
}
