package com.example.notification.service.unread;

import com.example.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationUnreadCountService {

    private static final String KEY_PREFIX = "notification:unread:";
    private static final Duration KEY_TTL = Duration.ofDays(7);
    private static final String DECREASE_SAFE_LUA = """
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            local delta = tonumber(ARGV[1])
            local next = current - delta
            if next < 0 then
              next = 0
            end
            redis.call('SET', KEYS[1], next)
            if tonumber(ARGV[2]) > 0 then
              redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
            end
            return next
            """;

    private final StringRedisTemplate stringRedisTemplate;
    private final NotificationRepository notificationRepository;

    public long getOrLoad(Long userId) {
        try {
            String cached = stringRedisTemplate.opsForValue().get(key(userId));
            if (cached != null) {
                return normalizeCount(parseLong(cached));
            }
        } catch (Exception e) {
            log.warn("Unread count cache lookup failed. userId={}", userId, e);
        }
        return refreshFromDb(userId);
    }

    public void increase(Long userId) {
        try {
            Long value = stringRedisTemplate.opsForValue().increment(key(userId));
            stringRedisTemplate.expire(key(userId), KEY_TTL);
            if (value != null && value < 0) {
                set(userId, 0);
            }
        } catch (Exception e) {
            log.warn("Unread count increment failed. userId={}", userId, e);
        }
    }

    public void decreaseSafely(Long userId, long delta) {
        if (delta <= 0) {
            return;
        }
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(DECREASE_SAFE_LUA, Long.class);
            stringRedisTemplate.execute(
                    script,
                    Collections.singletonList(key(userId)),
                    String.valueOf(delta),
                    String.valueOf(KEY_TTL.toSeconds())
            );
        } catch (Exception e) {
            log.warn("Unread count decrement failed. userId={}, delta={}", userId, delta, e);
        }
    }

    public void set(Long userId, long count) {
        try {
            stringRedisTemplate.opsForValue().set(
                    key(userId),
                    String.valueOf(normalizeCount(count)),
                    KEY_TTL
            );
        } catch (Exception e) {
            log.warn("Unread count cache set failed. userId={}, count={}", userId, count, e);
        }
    }

    public long refreshFromDb(Long userId) {
        long count = notificationRepository.countByRecipientIdAndIsReadFalse(userId);
        set(userId, count);
        return count;
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private long normalizeCount(long count) {
        return Math.max(0, count);
    }
}
