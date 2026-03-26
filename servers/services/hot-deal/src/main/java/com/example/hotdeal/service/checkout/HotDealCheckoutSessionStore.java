package com.example.hotdeal.service.checkout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class HotDealCheckoutSessionStore {

    private static final String SESSION_KEY_PREFIX = "hotdeal:checkout:session:";
    private static final String IDEMPOTENCY_KEY_PREFIX = "hotdeal:checkout:idempotency:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<HotDealCheckoutSession> findSession(Long orderId) {
        String payload = stringRedisTemplate.opsForValue().get(sessionKey(orderId));
        if (payload == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(objectMapper.readValue(payload, HotDealCheckoutSession.class));
        } catch (JsonProcessingException exception) {
            log.warn("Failed to deserialize hot-deal checkout session. orderId={}", orderId, exception);
            return Optional.empty();
        }
    }

    public Optional<HotDealCheckoutSession> findSessionByIdempotency(Long userId, String idempotencyKey) {
        String orderIdValue = stringRedisTemplate.opsForValue().get(idempotencyKey(userId, idempotencyKey));
        if (orderIdValue == null) {
            return Optional.empty();
        }
        try {
            return findSession(Long.parseLong(orderIdValue));
        } catch (NumberFormatException exception) {
            log.warn("Failed to parse hot-deal idempotency orderId. userId={}, idempotencyKey={}",
                    userId, idempotencyKey, exception);
            return Optional.empty();
        }
    }

    public void save(HotDealCheckoutSession session) {
        Duration ttl = ttlUntil(session.getExpiresAt());
        try {
            String payload = objectMapper.writeValueAsString(session);
            stringRedisTemplate.opsForValue().set(sessionKey(session.getOrderId()), payload, ttl);
            if (session.getIdempotencyKey() != null && !session.getIdempotencyKey().isBlank()) {
                stringRedisTemplate.opsForValue().set(
                        idempotencyKey(session.getUserId(), session.getIdempotencyKey()),
                        String.valueOf(session.getOrderId()),
                        ttl
                );
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize hot-deal checkout session", exception);
        }
    }

    public void delete(HotDealCheckoutSession session) {
        stringRedisTemplate.delete(sessionKey(session.getOrderId()));
        if (session.getIdempotencyKey() != null && !session.getIdempotencyKey().isBlank()) {
            stringRedisTemplate.delete(idempotencyKey(session.getUserId(), session.getIdempotencyKey()));
        }
    }

    public Set<Long> findExpiredReservedOrderIds(LocalDateTime now) {
        Set<Long> orderIds = new HashSet<>();
        for (String key : scanKeys(SESSION_KEY_PREFIX + "*")) {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (payload == null) {
                continue;
            }
            try {
                HotDealCheckoutSession session = objectMapper.readValue(payload, HotDealCheckoutSession.class);
                if (session.getStatus() == HotDealCheckoutSessionStatus.RESERVED && session.isExpired(now)) {
                    orderIds.add(session.getOrderId());
                }
            } catch (JsonProcessingException exception) {
                log.warn("Failed to parse checkout session while scanning expired sessions. key={}", key, exception);
            }
        }
        return orderIds;
    }

    private Duration ttlUntil(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            return Duration.ofMinutes(10);
        }
        Duration ttl = Duration.between(LocalDateTime.now(), expiresAt).plusMinutes(5);
        return ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(30) : ttl;
    }

    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(500)
                .build();
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            collectKeys(connection, options, keys);
            return null;
        });
        return keys;
    }

    private void collectKeys(RedisConnection connection, ScanOptions options, Set<String> keys) {
        try (Cursor<byte[]> cursor = connection.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
            }
        } catch (Exception exception) {
            log.warn("Failed to scan hot-deal checkout session keys", exception);
        }
    }

    private String sessionKey(Long orderId) {
        return SESSION_KEY_PREFIX + orderId;
    }

    private String idempotencyKey(Long userId, String idempotencyKey) {
        return IDEMPOTENCY_KEY_PREFIX + userId + ":" + idempotencyKey;
    }
}
