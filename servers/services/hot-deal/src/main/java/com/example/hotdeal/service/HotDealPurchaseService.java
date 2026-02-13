package com.example.hotdeal.service;

import com.example.core.exception.BusinessException;
import com.example.hotdeal.dto.HotDealPurchaseRequest;
import com.example.hotdeal.dto.HotDealPurchaseResponse;
import com.example.hotdeal.entity.HotDeal;
import com.example.hotdeal.entity.HotDealStatus;
import com.example.hotdeal.event.HotDealPurchasedEvent;
import com.example.hotdeal.exception.HotDealErrorCode;
import com.example.hotdeal.repository.HotDealRepository;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotDealPurchaseService {

    private final HotDealRepository hotDealRepository;
    private final EventPublisher eventPublisher;
    private final StringRedisTemplate stringRedisTemplate;
    private final QueueService queueService;

    private static final String STOCK_KEY_PREFIX = "hotdeal:stock:";
    private static final String RESERVATION_KEY_PREFIX = "hotdeal:reservation:";
    private static final String PURCHASED_KEY_PREFIX = "hotdeal:purchased:";
    private static final String MAX_PER_USER_KEY_PREFIX = "hotdeal:maxperuser:";
    private static final long RESERVATION_TTL_MINUTES = 5;

    private static final String LUA_SCRIPT = """
            local stockKey = KEYS[1]
            local reservationKey = KEYS[2]
            local purchasedKey = KEYS[3]
            local quantity = tonumber(ARGV[1])
            local ttl = tonumber(ARGV[2])
            local reservationId = ARGV[3]
            local maxPerUser = tonumber(ARGV[4])

            -- 유저당 구매 수량 체크
            local purchased = tonumber(redis.call('GET', purchasedKey) or '0')
            if purchased == nil then purchased = 0 end
            if purchased + quantity > maxPerUser then
                return -2
            end

            -- 재고 체크
            local current = tonumber(redis.call('GET', stockKey))
            if current == nil then
                return -1
            end
            if current < quantity then
                return 0
            end

            -- 재고 차감 + 예약 생성 + 구매 수량 기록
            redis.call('DECRBY', stockKey, quantity)
            redis.call('SET', reservationKey, reservationId, 'EX', ttl)
            redis.call('INCRBY', purchasedKey, quantity)
            return 1
            """;

    private static final String COMPENSATE_LUA_SCRIPT = """
            local stockKey = KEYS[1]
            local reservationKey = KEYS[2]
            local purchasedKey = KEYS[3]
            local reservationId = ARGV[1]
            local quantity = tonumber(ARGV[2])

            local currentReservation = redis.call('GET', reservationKey)
            if currentReservation == reservationId then
                redis.call('INCRBY', stockKey, quantity)

                local purchased = tonumber(redis.call('GET', purchasedKey) or '0')
                if purchased <= quantity then
                    redis.call('DEL', purchasedKey)
                else
                    redis.call('DECRBY', purchasedKey, quantity)
                end

                redis.call('DEL', reservationKey)
                return 1
            end

            return 0
            """;

    @Transactional
    public HotDealPurchaseResponse purchase(Long hotDealId, HotDealPurchaseRequest request, Long userId) {
        // 대기열 토큰 검증
        if (!queueService.isAdmitted(hotDealId, userId)) {
            throw new BusinessException(HotDealErrorCode.QUEUE_NOT_ADMITTED);
        }

        HotDeal hotDeal = hotDealRepository.findById(hotDealId)
                .orElseThrow(() -> new BusinessException(HotDealErrorCode.HOT_DEAL_NOT_FOUND));
        if (hotDeal.getStatus() != HotDealStatus.ACTIVE) {
            throw new BusinessException(HotDealErrorCode.HOT_DEAL_NOT_ACTIVE);
        }

        // maxPerUser 조회 (Redis 우선, 없으면 DB)
        String maxPerUserValue = stringRedisTemplate.opsForValue().get(MAX_PER_USER_KEY_PREFIX + hotDealId);
        int maxPerUser = maxPerUserValue != null
                ? Integer.parseInt(maxPerUserValue)
                : hotDeal.getMaxPerUser();

        String stockKey = STOCK_KEY_PREFIX + hotDealId;
        String reservationKey = RESERVATION_KEY_PREFIX + hotDealId + ":" + userId;
        String purchasedKey = PURCHASED_KEY_PREFIX + hotDealId + ":" + userId;
        String reservationId = UUID.randomUUID().toString();

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
        Long result = stringRedisTemplate.execute(script,
                List.of(stockKey, reservationKey, purchasedKey),
                String.valueOf(request.getQuantity()),
                String.valueOf(RESERVATION_TTL_MINUTES * 60),
                reservationId,
                String.valueOf(maxPerUser));

        if (result == null || result == -1) {
            throw new BusinessException(HotDealErrorCode.HOT_DEAL_NOT_FOUND);
        }

        if (result == -2) {
            throw new BusinessException(HotDealErrorCode.PURCHASE_QUANTITY_EXCEEDED);
        }

        if (result == 0) {
            throw new BusinessException(HotDealErrorCode.HOT_DEAL_SOLD_OUT);
        }

        try {
            // DB 판매 수량 원자적 증가 (동시성 안전)
            int updated = hotDealRepository.incrementSoldQuantity(hotDealId, request.getQuantity());
            if (updated == 0) {
                throw new BusinessException(HotDealErrorCode.HOT_DEAL_SOLD_OUT);
            }

            Long totalAmount = hotDeal.getDiscountedPrice() * request.getQuantity();

            // 이벤트 발행
            eventPublisher.publish(
                    new HotDealPurchasedEvent(
                            hotDealId, userId, hotDeal.getItemId(), request.getQuantity(), totalAmount
                    ),
                    EventMetadata.of("HotDeal", String.valueOf(hotDealId))
            );

            log.info("Hot deal purchased: hotDealId={}, userId={}, quantity={}", hotDealId, userId, request.getQuantity());

            return HotDealPurchaseResponse.success(reservationId,
                    LocalDateTime.now().plusMinutes(RESERVATION_TTL_MINUTES));
        } catch (RuntimeException e) {
            compensateReservation(stockKey, reservationKey, purchasedKey, reservationId, request.getQuantity());
            throw e;
        }
    }

    private void compensateReservation(String stockKey, String reservationKey, String purchasedKey,
                                       String reservationId, int quantity) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(COMPENSATE_LUA_SCRIPT, Long.class);
            stringRedisTemplate.execute(
                    script,
                    List.of(stockKey, reservationKey, purchasedKey),
                    reservationId,
                    String.valueOf(quantity)
            );
        } catch (Exception e) {
            // 보상 실패는 운영자가 추적할 수 있도록 별도 로그를 남긴다.
            log.error("Failed to compensate hot-deal reservation: reservationKey={}", reservationKey, e);
        }
    }
}
