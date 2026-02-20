package com.example.chat.service.policy;

import com.example.chat.config.ChatRateLimitProperties;
import com.example.chat.exception.ChatErrorCode;
import com.example.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageRateLimitService {

    private static final String SEC_KEY_PREFIX = "chat:ratelimit:msg:sec:";
    private static final String MIN_KEY_PREFIX = "chat:ratelimit:msg:min:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ChatRateLimitProperties properties;

    private final Map<String, AtomicLong> localCounters = new ConcurrentHashMap<>();

    public void validateMessageSendRate(Long senderId) {
        if (senderId == null || senderId <= 0) {
            return;
        }

        long nowEpochSecond = Instant.now().getEpochSecond();
        long epochMinute = nowEpochSecond / 60L;

        String secKey = SEC_KEY_PREFIX + senderId + ":" + nowEpochSecond;
        String minKey = MIN_KEY_PREFIX + senderId + ":" + epochMinute;

        long secondCount;
        long minuteCount;
        try {
            secondCount = incrementRedisCounter(secKey);
            minuteCount = incrementRedisCounter(minKey);
        } catch (Exception e) {
            log.debug("Redis rate-limit failed. fallback local. senderId={}", senderId, e);
            secondCount = incrementLocalCounter(secKey);
            minuteCount = incrementLocalCounter(minKey);
        }

        if (secondCount > Math.max(1, properties.getPerSecond())
                || minuteCount > Math.max(1, properties.getPerMinute())) {
            throw new BusinessException(ChatErrorCode.MESSAGE_RATE_LIMIT_EXCEEDED);
        }
    }

    private long incrementRedisCounter(String key) {
        long ttl = Math.max(10, properties.getKeyTtlSeconds());
        Long value = stringRedisTemplate.opsForValue().increment(key);
        if (value != null && value > 0) {
            stringRedisTemplate.expire(key, Duration.ofSeconds(ttl));
        }
        return value == null ? 0 : value;
    }

    private long incrementLocalCounter(String key) {
        if (localCounters.size() > 20000) {
            localCounters.clear();
        }
        return localCounters.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    }
}
