package com.example.hotdeal.service.query;

import com.example.hotdeal.dto.query.response.HotDealDetailQueryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class HotDealDetailCache {

    private static final String CACHE_KEY_PREFIX = "hotdeal:detail:";
    private static final long CACHE_TTL_SECONDS = 10;

    private final RedisTemplate<String, Object> redisTemplate;

    public Optional<HotDealDetailQueryResponse> get(Long hotDealId) {
        Object cached = redisTemplate.opsForValue().get(cacheKey(hotDealId));
        if (cached instanceof HotDealDetailQueryResponse response) {
            return Optional.of(response);
        }
        return Optional.empty();
    }

    public void put(Long hotDealId, HotDealDetailQueryResponse response) {
        redisTemplate.opsForValue().set(cacheKey(hotDealId), response, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
    }

    static String cacheKey(Long hotDealId) {
        return CACHE_KEY_PREFIX + hotDealId;
    }
}
