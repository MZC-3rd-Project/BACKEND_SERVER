package com.example.sales.service.command;

import com.example.core.exception.BusinessException;
import com.example.sales.dto.checkout.CheckoutQuoteCache;
import com.example.sales.exception.SalesErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CheckoutQuoteCacheRedisService {

    private static final String QUOTE_CACHE_KEY_PREFIX = "sales:checkout:quote:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public void saveQuote(CheckoutQuoteCache quoteCache, Duration ttl) {
        redisTemplate.opsForValue().set(quoteKey(quoteCache.getOrderId()), serializeQuote(quoteCache), ttl);
    }

    public Optional<CheckoutQuoteCache> findQuote(Long orderId) {
        Object value = redisTemplate.opsForValue().get(quoteKey(orderId));
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof CheckoutQuoteCache quoteCache) {
            return Optional.of(quoteCache);
        }
        if (value instanceof String json) {
            return Optional.of(deserializeQuote(json));
        }
        return Optional.of(objectMapper.convertValue(value, CheckoutQuoteCache.class));
    }

    public void deleteQuote(Long orderId) {
        redisTemplate.delete(quoteKey(orderId));
    }

    private String quoteKey(Long orderId) {
        return QUOTE_CACHE_KEY_PREFIX + orderId;
    }

    private String serializeQuote(CheckoutQuoteCache quoteCache) {
        try {
            return objectMapper.writeValueAsString(quoteCache);
        } catch (JsonProcessingException e) {
            throw new BusinessException(SalesErrorCode.INVALID_REQUEST);
        }
    }

    private CheckoutQuoteCache deserializeQuote(String json) {
        try {
            return objectMapper.readValue(json, CheckoutQuoteCache.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(SalesErrorCode.INVALID_REQUEST);
        }
    }
}
