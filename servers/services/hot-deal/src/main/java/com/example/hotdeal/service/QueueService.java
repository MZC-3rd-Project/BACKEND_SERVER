package com.example.hotdeal.service;

import com.example.core.exception.BusinessException;
import com.example.hotdeal.dto.QueueEnterResponse;
import com.example.hotdeal.dto.QueueStatusResponse;
import com.example.hotdeal.exception.HotDealErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String QUEUE_KEY_PREFIX = "hotdeal:queue:";
    private static final String TOKEN_KEY_PREFIX = "hotdeal:token:";
    private static final String ADMITTED_KEY_PREFIX = "hotdeal:admitted:";
    private static final long TOKEN_TTL_MINUTES = 30;
    private static final long ADMITTED_TTL_MINUTES = 10;
    private static final long ESTIMATED_PROCESS_SECONDS_PER_USER = 2;

    public QueueEnterResponse enter(Long hotDealId, Long userId) {
        String queueKey = QUEUE_KEY_PREFIX + hotDealId;
        String memberKey = userId.toString();

        // 이미 대기열에 있는지 확인
        Long existingRank = redisTemplate.opsForZSet().rank(queueKey, memberKey);
        if (existingRank != null) {
            throw new BusinessException(HotDealErrorCode.QUEUE_ALREADY_ENTERED);
        }

        // 이미 입장 허용된 사용자인지 확인
        if (isAdmitted(hotDealId, userId)) {
            throw new BusinessException(HotDealErrorCode.QUEUE_ALREADY_ENTERED);
        }

        // ZADD (score = timestamp)
        double score = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(queueKey, memberKey, score);

        // 토큰 생성
        String token = UUID.randomUUID().toString();
        String tokenKey = TOKEN_KEY_PREFIX + hotDealId + ":" + userId;
        redisTemplate.opsForValue().set(tokenKey, token, TOKEN_TTL_MINUTES, TimeUnit.MINUTES);

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
    public void admitUsers(Long hotDealId, int count) {
        String queueKey = QUEUE_KEY_PREFIX + hotDealId;

        Set<ZSetOperations.TypedTuple<Object>> topUsers =
                redisTemplate.opsForZSet().popMin(queueKey, count);

        if (topUsers == null || topUsers.isEmpty()) {
            return;
        }

        for (ZSetOperations.TypedTuple<Object> user : topUsers) {
            Object value = user.getValue();
            if (value != null) {
                String admittedKey = ADMITTED_KEY_PREFIX + hotDealId + ":" + value;
                redisTemplate.opsForValue().set(admittedKey, "true", ADMITTED_TTL_MINUTES, TimeUnit.MINUTES);
                log.debug("User admitted: hotDealId={}, userId={}", hotDealId, value);
            }
        }
    }

    public boolean isAdmitted(Long hotDealId, Long userId) {
        String admittedKey = ADMITTED_KEY_PREFIX + hotDealId + ":" + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(admittedKey));
    }
}
