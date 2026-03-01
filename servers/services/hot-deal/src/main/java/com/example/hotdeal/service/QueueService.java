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

    private static final String QUEUE_KEY_PREFIX = "hotdeal:queue:";
    private static final String TOKEN_KEY_PREFIX = "hotdeal:token:";
    private static final String ADMITTED_KEY_PREFIX = "hotdeal:admitted:";
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
                String admittedKey = ADMITTED_KEY_PREFIX + hotDealId + ":" + value;
                redisTemplate.opsForValue().set(admittedKey, "true", ADMITTED_TTL_MINUTES, TimeUnit.MINUTES);
                log.debug("User admitted: hotDealId={}, userId={}", hotDealId, value);
                try {
                    admittedUserIds.add(Long.parseLong(String.valueOf(value)));
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse admitted user id. hotDealId={}, rawUserId={}", hotDealId, value);
                }
            }
        }

        return admittedUserIds;
    }

    public boolean isAdmitted(Long hotDealId, Long userId) {
        String admittedKey = ADMITTED_KEY_PREFIX + hotDealId + ":" + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(admittedKey));
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
}
