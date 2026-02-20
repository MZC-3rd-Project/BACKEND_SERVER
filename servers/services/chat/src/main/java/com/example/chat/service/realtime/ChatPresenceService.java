package com.example.chat.service.realtime;

import com.example.chat.config.ChatRealtimeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatPresenceService {

    private static final String PRESENCE_KEY_PREFIX = "chat:presence:user:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ChatRealtimeProperties chatRealtimeProperties;

    private final Map<Long, AtomicLong> localPresence = new ConcurrentHashMap<>();

    public void markConnected(Long userId) {
        if (!isValidUserId(userId)) {
            return;
        }
        String key = key(userId);
        long ttlSeconds = Math.max(10L, chatRealtimeProperties.getPresenceTtlSeconds());

        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count > 0) {
                stringRedisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
            }
        } catch (Exception e) {
            log.debug("Redis presence connect failed. fallback local. userId={}", userId, e);
            localPresence.computeIfAbsent(userId, ignored -> new AtomicLong()).incrementAndGet();
        }
    }

    public void markHeartbeat(Long userId) {
        if (!isValidUserId(userId)) {
            return;
        }
        String key = key(userId);
        long ttlSeconds = Math.max(10L, chatRealtimeProperties.getPresenceTtlSeconds());

        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                stringRedisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
            }
        } catch (Exception e) {
            log.debug("Redis presence heartbeat failed. userId={}", userId, e);
        }
    }

    public void markDisconnected(Long userId) {
        if (!isValidUserId(userId)) {
            return;
        }
        String key = key(userId);
        long ttlSeconds = Math.max(10L, chatRealtimeProperties.getPresenceTtlSeconds());

        try {
            Long count = stringRedisTemplate.opsForValue().decrement(key);
            if (count == null || count <= 0) {
                stringRedisTemplate.delete(key);
            } else {
                stringRedisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
            }
        } catch (Exception e) {
            log.debug("Redis presence disconnect failed. fallback local. userId={}", userId, e);
            localPresence.computeIfPresent(userId, (ignored, value) -> {
                if (value.decrementAndGet() <= 0) {
                    return null;
                }
                return value;
            });
        }
    }

    public boolean isOnline(Long userId) {
        if (!isValidUserId(userId)) {
            return false;
        }
        String key = key(userId);
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value == null) {
                return false;
            }
            return Long.parseLong(value) > 0;
        } catch (Exception e) {
            AtomicLong local = localPresence.get(userId);
            return local != null && local.get() > 0;
        }
    }

    private boolean isValidUserId(Long userId) {
        return userId != null && userId > 0;
    }

    private String key(Long userId) {
        return PRESENCE_KEY_PREFIX + userId;
    }
}
