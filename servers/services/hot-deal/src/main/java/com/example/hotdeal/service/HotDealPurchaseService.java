package com.example.hotdeal.service;

import com.example.core.exception.BusinessException;
import com.example.hotdeal.dto.HotDealPurchaseRequest;
import com.example.hotdeal.dto.HotDealPurchaseResponse;
import com.example.hotdeal.entity.HotDeal;
import com.example.hotdeal.event.HotDealPurchasedEvent;
import com.example.hotdeal.exception.HotDealErrorCode;
import com.example.hotdeal.repository.HotDealRepository;
import com.example.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotDealPurchaseService {

    private final HotDealRepository hotDealRepository;
    private final EventPublisher eventPublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    private final QueueService queueService;

    private static final String STOCK_KEY_PREFIX = "hotdeal:stock:";
    private static final String RESERVATION_KEY_PREFIX = "hotdeal:reservation:";
    private static final long RESERVATION_TTL_MINUTES = 5;

    private static final String LUA_SCRIPT = """
            local stockKey = KEYS[1]
            local reservationKey = KEYS[2]
            local quantity = tonumber(ARGV[1])
            local ttl = tonumber(ARGV[2])
            local reservationId = ARGV[3]

            local current = tonumber(redis.call('GET', stockKey))
            if current == nil then
                return -1
            end
            if current < quantity then
                return 0
            end
            redis.call('DECRBY', stockKey, quantity)
            redis.call('SET', reservationKey, reservationId, 'EX', ttl)
            return 1
            """;

    @Transactional
    public HotDealPurchaseResponse purchase(Long hotDealId, HotDealPurchaseRequest request, Long userId) {
        // 대기열 토큰 검증
        if (!queueService.isAdmitted(hotDealId, userId)) {
            throw new BusinessException(HotDealErrorCode.QUEUE_NOT_ADMITTED);
        }

        String stockKey = STOCK_KEY_PREFIX + hotDealId;
        String reservationKey = RESERVATION_KEY_PREFIX + hotDealId + ":" + userId;
        String reservationId = UUID.randomUUID().toString();

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script,
                List.of(stockKey, reservationKey),
                request.getQuantity(),
                RESERVATION_TTL_MINUTES * 60,
                reservationId);

        if (result == null || result == -1) {
            throw new BusinessException(HotDealErrorCode.HOT_DEAL_NOT_FOUND);
        }

        if (result == 0) {
            throw new BusinessException(HotDealErrorCode.HOT_DEAL_SOLD_OUT);
        }

        // DB 판매 수량 원자적 증가 (동시성 안전)
        hotDealRepository.incrementSoldQuantity(hotDealId, request.getQuantity());

        HotDeal hotDeal = hotDealRepository.findById(hotDealId)
                .orElseThrow(() -> new BusinessException(HotDealErrorCode.HOT_DEAL_NOT_FOUND));

        Long totalAmount = hotDeal.getDiscountedPrice() * request.getQuantity();

        // 이벤트 발행
        eventPublisher.publish(new HotDealPurchasedEvent(
                hotDealId, userId, hotDeal.getItemId(), request.getQuantity(), totalAmount));

        log.info("Hot deal purchased: hotDealId={}, userId={}, quantity={}", hotDealId, userId, request.getQuantity());

        return HotDealPurchaseResponse.success(reservationId,
                LocalDateTime.now().plusMinutes(RESERVATION_TTL_MINUTES));
    }
}
