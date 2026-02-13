package com.example.hotdeal.service;

import com.example.config.redis.lock.DistributedLock;
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
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotDealCommandService {

    private final HotDealRepository hotDealRepository;
    private final HotDealStatusHistoryRepository statusHistoryRepository;
    private final EventPublisher eventPublisher;
    private final StringRedisTemplate stringRedisTemplate;
    private final ProductClient productClient;
    private final TransactionTemplate transactionTemplate;

    private static final String STOCK_KEY_PREFIX = "hotdeal:stock:";
    private static final String MAX_PER_USER_KEY_PREFIX = "hotdeal:maxperuser:";
    private static final String QUEUE_KEY_PREFIX = "hotdeal:queue:";
    private static final String TOKEN_KEY_PREFIX = "hotdeal:token:";
    private static final String ADMITTED_KEY_PREFIX = "hotdeal:admitted:";
    private static final String PURCHASED_KEY_PREFIX = "hotdeal:purchased:";
    private static final String RESERVATION_KEY_PREFIX = "hotdeal:reservation:";

    /**
     * 판매자가 직접 핫딜 생성 — HTTP 조회 후 트랜잭션 시작
     */
    @DistributedLock(key = "'hotdeal:item:' + #request.itemId", waitTime = 3)
    public HotDealDetailResponse createManual(CreateHotDealRequest request, Long userId) {
        // 상품 정보 조회 (트랜잭션 밖)
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
        Integer maxPerUser = request.getMaxPerUser() != null ? request.getMaxPerUser() : 1;

        // DB 작업은 트랜잭션 내에서 (self-invocation이므로 TransactionTemplate 사용)
        HotDeal hotDeal = transactionTemplate.execute(status ->
                createAndActivate(request.getItemId(), title, originalPrice,
                        request.getDiscountRate(), request.getMaxQuantity(), maxPerUser,
                        startAt, endAt, "판매자 수동 생성 (userId=" + userId + ")"));

        return HotDealDetailResponse.from(hotDeal);
    }

    @Transactional
    @DistributedLock(key = "'hotdeal:item:' + #itemId", waitTime = 3)
    public HotDeal createAndActivate(Long itemId, String title, Long originalPrice,
                                      Integer discountRate, Integer maxQuantity, Integer maxPerUser,
                                      LocalDateTime startAt, LocalDateTime endAt, String reason) {
        boolean exists = hotDealRepository.existsByItemIdAndStatusIn(
                itemId, List.of(HotDealStatus.SCHEDULED, HotDealStatus.ACTIVE));
        if (exists) {
            throw new BusinessException(HotDealErrorCode.HOT_DEAL_ALREADY_EXISTS);
        }

        HotDeal hotDeal = HotDeal.create(itemId, title, originalPrice,
                discountRate, maxQuantity, maxPerUser, startAt, endAt);
        hotDeal.activate();
        hotDealRepository.save(hotDeal);

        // 상태 이력
        statusHistoryRepository.save(
                HotDealStatusHistory.create(hotDeal.getId(), null, HotDealStatus.SCHEDULED, "핫딜 생성"));
        statusHistoryRepository.save(
                HotDealStatusHistory.create(hotDeal.getId(), HotDealStatus.SCHEDULED, HotDealStatus.ACTIVE, reason));

        // 트랜잭션 커밋 이후 Redis에 재고 + 유저당 최대 수량을 초기화한다.
        Long hotDealId = hotDeal.getId();
        runAfterCommit(() -> {
            stringRedisTemplate.opsForValue().set(STOCK_KEY_PREFIX + hotDealId, String.valueOf(maxQuantity));
            stringRedisTemplate.opsForValue().set(MAX_PER_USER_KEY_PREFIX + hotDealId, String.valueOf(maxPerUser));
        });

        // 이벤트 발행
        eventPublisher.publish(
                new HotDealStartedEvent(
                        hotDealId, itemId, title, hotDeal.getDiscountedPrice(),
                        discountRate, maxQuantity, startAt, endAt
                ),
                EventMetadata.of("HotDeal", String.valueOf(hotDealId))
        );

        log.info("Hot deal activated: id={}, itemId={}, discountRate={}%", hotDeal.getId(), itemId, discountRate);

        return hotDeal;
    }

    public void endExpiredDeals() {
        List<Long> expiredDealIds = hotDealRepository.findExpiredActiveDeals(LocalDateTime.now()).stream()
                .map(HotDeal::getId)
                .toList();

        for (Long hotDealId : expiredDealIds) {
            try {
                transactionTemplate.executeWithoutResult(status -> endExpiredDealInTransaction(hotDealId));
            } catch (Exception e) {
                log.error("Failed to end hot deal: id={}", hotDealId, e);
            }
        }
    }

    private void endExpiredDealInTransaction(Long hotDealId) {
        HotDeal hotDeal = hotDealRepository.findById(hotDealId).orElse(null);
        if (hotDeal == null || hotDeal.getStatus() != HotDealStatus.ACTIVE) {
            return;
        }

        HotDealStatus from = hotDeal.getStatus();
        hotDeal.end();

        statusHistoryRepository.save(
                HotDealStatusHistory.create(hotDeal.getId(), from, HotDealStatus.ENDED, "시간 만료"));

        runAfterCommit(() -> clearRedisKeys(hotDealId));

        eventPublisher.publish(
                new HotDealEndedEvent(
                        hotDealId, hotDeal.getItemId(),
                        hotDeal.getSoldQuantity(), hotDeal.getMaxQuantity()
                ),
                EventMetadata.of("HotDeal", String.valueOf(hotDealId))
        );

        log.info("Hot deal ended: id={}, sold={}/{}", hotDeal.getId(),
                hotDeal.getSoldQuantity(), hotDeal.getMaxQuantity());
    }

    private void clearRedisKeys(Long hotDealId) {
        stringRedisTemplate.delete(STOCK_KEY_PREFIX + hotDealId);
        stringRedisTemplate.delete(MAX_PER_USER_KEY_PREFIX + hotDealId);
        stringRedisTemplate.delete(QUEUE_KEY_PREFIX + hotDealId);
        deleteKeysByPattern(ADMITTED_KEY_PREFIX + hotDealId + ":*");
        deleteKeysByPattern(TOKEN_KEY_PREFIX + hotDealId + ":*");
        deleteKeysByPattern(PURCHASED_KEY_PREFIX + hotDealId + ":*");
        deleteKeysByPattern(RESERVATION_KEY_PREFIX + hotDealId + ":*");
    }

    private void deleteKeysByPattern(String pattern) {
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return;
        }

        stringRedisTemplate.delete(keys);
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }

        action.run();
    }
}
