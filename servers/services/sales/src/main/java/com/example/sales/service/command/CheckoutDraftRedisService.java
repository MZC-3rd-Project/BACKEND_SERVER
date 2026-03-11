package com.example.sales.service.command;

import com.example.sales.dto.checkout.CheckoutDraft;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.core.exception.BusinessException;
import com.example.sales.exception.SalesErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CheckoutDraftRedisService {

    private static final String DRAFT_KEY_PREFIX = "sales:checkout:draft:";
    private static final String IDEMPOTENCY_KEY_PREFIX = "sales:checkout:idempotency:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public void saveDraft(CheckoutDraft draft, Duration ttl) {
        redisTemplate.opsForValue().set(draftKey(draft.getOrderId()), serializeDraft(draft), ttl);
        redisTemplate.opsForValue().set(idempotencyKey(draft.getUserId(), draft.getIdempotencyKey()), String.valueOf(draft.getOrderId()), ttl);
    }

    public Optional<CheckoutDraft> findDraft(Long orderId) {
        Object value = redisTemplate.opsForValue().get(draftKey(orderId));
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof CheckoutDraft draft) {
            return Optional.of(draft);
        }
        if (value instanceof String json) {
            return Optional.of(deserializeDraft(json));
        }
        return Optional.of(objectMapper.convertValue(value, CheckoutDraft.class));
    }

    public Optional<Long> findOrderIdByIdempotencyKey(Long userId, String idempotencyKey) {
        Object value = redisTemplate.opsForValue().get(idempotencyKey(userId, idempotencyKey));
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Number number) {
            return Optional.of(number.longValue());
        }
        return Optional.of(Long.parseLong(String.valueOf(value)));
    }

    public void deleteDraft(Long orderId) {
        redisTemplate.delete(draftKey(orderId));
    }

    public void deleteDraft(Long orderId, Long userId, String idempotencyKey) {
        redisTemplate.delete(draftKey(orderId));
        redisTemplate.delete(idempotencyKey(userId, idempotencyKey));
    }

    private String draftKey(Long orderId) {
        return DRAFT_KEY_PREFIX + orderId;
    }

    private String idempotencyKey(Long userId, String idempotencyKey) {
        return IDEMPOTENCY_KEY_PREFIX + userId + ":" + idempotencyKey;
    }

    private String serializeDraft(CheckoutDraft draft) {
        try {
            return objectMapper.writeValueAsString(draft);
        } catch (JsonProcessingException e) {
            throw new BusinessException(SalesErrorCode.CHECKOUT_SESSION_NOT_FOUND);
        }
    }

    private CheckoutDraft deserializeDraft(String json) {
        try {
            return objectMapper.readValue(json, CheckoutDraft.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(SalesErrorCode.CHECKOUT_SESSION_NOT_FOUND);
        }
    }
}
