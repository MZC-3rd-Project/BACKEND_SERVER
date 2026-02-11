package com.example.stock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockCacheService {

    private static final String STOCK_KEY_PREFIX = "stock:";
    private static final Duration STOCK_TTL = Duration.ofHours(1);

    private final RedisTemplate<String, Object> redisTemplate;

    // Lua Script: 원자적 재고 확인 + 차감
    private static final String DECREASE_LUA_SCRIPT =
            "local current = tonumber(redis.call('GET', KEYS[1]) or -1) " +
            "if current < 0 then return -1 end " +
            "if current < tonumber(ARGV[1]) then return -2 end " +
            "return redis.call('DECRBY', KEYS[1], ARGV[1])";

    // Lua Script: 원자적 재고 증가 (상한 체크)
    private static final String INCREASE_LUA_SCRIPT =
            "local current = tonumber(redis.call('GET', KEYS[1]) or -1) " +
            "if current < 0 then return -1 end " +
            "local max = tonumber(ARGV[2]) " +
            "local added = tonumber(ARGV[1]) " +
            "local result = current + added " +
            "if result > max then result = max end " +
            "redis.call('SET', KEYS[1], result) " +
            "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3])) " +
            "return result";

    public void cacheStock(Long stockItemId, int availableQuantity) {
        String key = buildKey(stockItemId);
        redisTemplate.opsForValue().set(key, availableQuantity, STOCK_TTL);
        log.debug("캐시 저장: {} = {}", key, availableQuantity);
    }

    public Integer getStock(Long stockItemId) {
        String key = buildKey(stockItemId);
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) return null;
        return ((Number) value).intValue();
    }

    /**
     * Lua Script로 원자적 재고 차감.
     * @return 차감 후 잔여 수량. -1이면 캐시 미스, -2이면 재고 부족
     */
    public long decrementStock(Long stockItemId, int quantity) {
        String key = buildKey(stockItemId);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(DECREASE_LUA_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(key), quantity);
        return result != null ? result : -1;
    }

    /**
     * Lua Script로 원자적 재고 증가 (totalQuantity 상한).
     */
    public long incrementStock(Long stockItemId, int quantity, int totalQuantity) {
        String key = buildKey(stockItemId);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(INCREASE_LUA_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(key),
                quantity, totalQuantity, STOCK_TTL.toSeconds());
        return result != null ? result : -1;
    }

    public void invalidateStock(Long stockItemId) {
        String key = buildKey(stockItemId);
        redisTemplate.delete(key);
        log.debug("캐시 무효화: {}", key);
    }

    private String buildKey(Long stockItemId) {
        return STOCK_KEY_PREFIX + stockItemId;
    }
}
