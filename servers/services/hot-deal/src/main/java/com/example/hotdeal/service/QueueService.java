package com.example.hotdeal.service;

import com.example.core.exception.BusinessException;
import com.example.hotdeal.dto.QueueEnterResponse;
import com.example.hotdeal.dto.QueueStatusResponse;
import com.example.hotdeal.exception.HotDealErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String QUEUE_KEY_PREFIX = "hotdeal:queue:";
    private static final String TOKEN_KEY_PREFIX = "hotdeal:token:";
    private static final String ADMITTED_KEY_PREFIX = "hotdeal:admitted:";
    private static final String ADMITTED_SLOT_KEY_PREFIX = "hotdeal:admitted:slots:";
    private static final String STOCK_KEY_PREFIX = "hotdeal:stock:";
    private static final long TOKEN_TTL_MINUTES = 30;
    private static final long ADMITTED_TTL_MINUTES = 10;
    private static final long ESTIMATED_PROCESS_SECONDS_PER_USER = 2;

    public QueueEnterResponse enter(Long hotDealId, Long userId) {
        String queueKey = QUEUE_KEY_PREFIX + hotDealId;
        String memberKey = userId.toString();
        String token = resolveOrIssueToken(hotDealId, userId);

        // 이미 대기열에 있으면 멱등 응답 (재진입으로 순번이 뒤로 밀리지 않음)
        Long existingRank = redisTemplate.opsForZSet().rank(queueKey, memberKey);
        if (existingRank != null) {
            long position = existingRank + 1;
            return QueueEnterResponse.builder()
                    .token(token)
                    .position(position)
                    .estimatedWaitSeconds(position * ESTIMATED_PROCESS_SECONDS_PER_USER)
                    .build();
        }

        // 이미 입장 허용된 사용자도 멱등 응답
        if (isAdmitted(hotDealId, userId)) {
            return QueueEnterResponse.builder()
                    .token(token)
                    .position(0L)
                    .estimatedWaitSeconds(0L)
                    .build();
        }

        // ZADD (score = timestamp)
        double score = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(queueKey, memberKey, score);

        Long position = redisTemplate.opsForZSet().rank(queueKey, memberKey);
        long pos = position != null ? position + 1 : 1;

        return QueueEnterResponse.builder()
                .token(token)
                .position(pos)
                .estimatedWaitSeconds(pos * ESTIMATED_PROCESS_SECONDS_PER_USER)
                .build();
    }

    public QueueStatusResponse getStatus(Long hotDealId, Long userId) {
        // 이미 입장 허용됐는지 확인
        if (isAdmitted(hotDealId, userId)) {
            return QueueStatusResponse.builder()
                    .position(0L)
                    .canPurchase(true)
                    .build();
        }

        String queueKey = QUEUE_KEY_PREFIX + hotDealId;
        String memberKey = userId.toString();

        Long rank = redisTemplate.opsForZSet().rank(queueKey, memberKey);
        if (rank == null) {
            throw new BusinessException(HotDealErrorCode.QUEUE_TOKEN_INVALID);
        }

        return QueueStatusResponse.builder()
                .position(rank + 1)
                .canPurchase(false)
                .build();
    }

    /**
     * 상위 N명 입장 허용 (스케줄러에서 호출)
     */
    public Set<Long> admitUsers(Long hotDealId, int count) {
        String queueKey = QUEUE_KEY_PREFIX + hotDealId;

        Set<ZSetOperations.TypedTuple<Object>> topUsers =
                redisTemplate.opsForZSet().popMin(queueKey, count);

        if (topUsers == null || topUsers.isEmpty()) {
            return Set.of();
        }

        Set<Long> admittedUserIds = new HashSet<>();
        for (ZSetOperations.TypedTuple<Object> user : topUsers) {
            Object value = user.getValue();
            if (value != null) {
                String userId = String.valueOf(value);
                String admittedKey = ADMITTED_KEY_PREFIX + hotDealId + ":" + userId;
                redisTemplate.opsForValue().set(admittedKey, "true", ADMITTED_TTL_MINUTES, TimeUnit.MINUTES);
                trackAdmissionSlot(hotDealId, userId);
                log.debug("User admitted: hotDealId={}, userId={}", hotDealId, userId);
                try {
                    admittedUserIds.add(Long.parseLong(userId));
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse admitted user id. hotDealId={}, rawUserId={}", hotDealId, value);
                }
            }
        }

        return admittedUserIds;
    }

    /**
     * 현재 남은 재고 수량과 동시 처리 한도 중 더 작은 값을 목표 슬롯 수로 삼고,
     * 이미 슬롯을 점유 중인 인원을 제외한 만큼만 추가 입장 허용한다.
     */
    public Set<Long> admitUsersByAvailableStock(Long hotDealId, int maxConcurrentAdmissions) {
        if (maxConcurrentAdmissions <= 0) {
            cleanupExpiredAdmissionSlots(hotDealId);
            return Set.of();
        }

        int availableStock = resolveAvailableStock(hotDealId);
        if (availableStock <= 0) {
            cleanupExpiredAdmissionSlots(hotDealId);
            return Set.of();
        }

        int targetOpenSlots = Math.min(availableStock, maxConcurrentAdmissions);
        long activeSlots = countActiveAdmissionSlots(hotDealId);
        long additionalUsers = targetOpenSlots - activeSlots;
        if (additionalUsers <= 0) {
            return Set.of();
        }

        int admitCount = (int) Math.min(additionalUsers, Integer.MAX_VALUE);
        return admitUsers(hotDealId, admitCount);
    }

    public boolean isAdmitted(Long hotDealId, Long userId) {
        String admittedKey = ADMITTED_KEY_PREFIX + hotDealId + ":" + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(admittedKey));
    }

    public void releaseAdmissionSlot(Long hotDealId, Long userId) {
        stringRedisTemplate.opsForZSet().remove(admissionSlotKey(hotDealId), userId.toString());
    }

    public void consumeAdmission(Long hotDealId, Long userId) {
        redisTemplate.delete(ADMITTED_KEY_PREFIX + hotDealId + ":" + userId);
        redisTemplate.delete(TOKEN_KEY_PREFIX + hotDealId + ":" + userId);
        releaseAdmissionSlot(hotDealId, userId);
    }

    private String resolveOrIssueToken(Long hotDealId, Long userId) {
        String tokenKey = TOKEN_KEY_PREFIX + hotDealId + ":" + userId;
        Object stored = redisTemplate.opsForValue().get(tokenKey);
        if (stored instanceof String storedToken && StringUtils.hasText(storedToken)) {
            return storedToken;
        }

        String issued = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(tokenKey, issued, TOKEN_TTL_MINUTES, TimeUnit.MINUTES);
        return issued;
    }

    /**
     * 하위 호환:
     * - 저장된 토큰이 없으면(과거 클라이언트/만료) true
     * - 저장된 토큰이 있으면 제공 토큰과 일치해야 true
     */
    public boolean isTokenValid(Long hotDealId, Long userId, String token) {
        String tokenKey = TOKEN_KEY_PREFIX + hotDealId + ":" + userId;
        Object stored = redisTemplate.opsForValue().get(tokenKey);
        if (stored == null) {
            return true;
        }
        if (!StringUtils.hasText(token)) {
            return false;
        }
        return token.trim().equals(String.valueOf(stored));
    }

    private int resolveAvailableStock(Long hotDealId) {
        String stockValue = stringRedisTemplate.opsForValue().get(STOCK_KEY_PREFIX + hotDealId);
        if (!StringUtils.hasText(stockValue)) {
            return 0;
        }

        try {
            return Math.max(Integer.parseInt(stockValue.trim()), 0);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse hot-deal stock. hotDealId={}, rawStock={}", hotDealId, stockValue);
            return 0;
        }
    }

    private long countActiveAdmissionSlots(Long hotDealId) {
        cleanupExpiredAdmissionSlots(hotDealId);
        Long count = stringRedisTemplate.opsForZSet().zCard(admissionSlotKey(hotDealId));
        return count != null ? count : 0L;
    }

    private void cleanupExpiredAdmissionSlots(Long hotDealId) {
        stringRedisTemplate.opsForZSet().removeRangeByScore(
                admissionSlotKey(hotDealId),
                Double.NEGATIVE_INFINITY,
                System.currentTimeMillis()
        );
    }

    private void trackAdmissionSlot(Long hotDealId, String userId) {
        long expiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(ADMITTED_TTL_MINUTES);
        stringRedisTemplate.opsForZSet().add(admissionSlotKey(hotDealId), userId, expiresAt);
    }

    private String admissionSlotKey(Long hotDealId) {
        return ADMITTED_SLOT_KEY_PREFIX + hotDealId;
    }
}
