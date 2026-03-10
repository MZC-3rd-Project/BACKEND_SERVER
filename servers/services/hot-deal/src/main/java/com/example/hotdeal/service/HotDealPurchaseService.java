package com.example.hotdeal.service;

import com.example.core.id.Snowflake;
import com.example.core.exception.BusinessException;
import com.example.hotdeal.dto.HotDealPurchaseRequest;
import com.example.hotdeal.dto.HotDealPurchaseResponse;
import com.example.hotdeal.entity.HotDeal;
import com.example.hotdeal.entity.HotDealStatus;
import com.example.hotdeal.exception.HotDealErrorCode;
import com.example.hotdeal.repository.HotDealRepository;
import com.example.hotdeal.service.checkout.HotDealCheckoutCommand;
import com.example.hotdeal.service.checkout.HotDealCheckoutProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotDealPurchaseService {

    private final HotDealRepository hotDealRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final QueueService queueService;
    private final HotDealCheckoutProcessor hotDealCheckoutProcessor;
    private final Snowflake snowflake;

    private static final String MAX_PER_USER_KEY_PREFIX = "hotdeal:maxperuser:";

    @Value("${hotdeal.queue.require-token-on-purchase:false}")
    private boolean requireTokenOnPurchase;

    @Transactional
    public HotDealPurchaseResponse purchase(Long hotDealId, HotDealPurchaseRequest request, Long userId) {
        validateQueueAdmission(hotDealId, userId, request.getToken());

        HotDeal hotDeal = findActiveHotDeal(hotDealId);
        int maxPerUser = resolveMaxPerUser(hotDealId, hotDeal.getMaxPerUser());
        Long orderId = snowflake.nextId();

        HotDealCheckoutCommand command = new HotDealCheckoutCommand(
                hotDealId,
                orderId,
                hotDeal.getItemId(),
                userId,
                request.getQuantity(),
                hotDeal.getDiscountedPrice(),
                maxPerUser
        );

        return hotDealCheckoutProcessor.checkout(command);
    }

    private void validateQueueAdmission(Long hotDealId, Long userId, String token) {
        if (!queueService.isAdmitted(hotDealId, userId)) {
            throw new BusinessException(HotDealErrorCode.QUEUE_NOT_ADMITTED);
        }
        if (!queueService.isTokenValid(hotDealId, userId, token)) {
            if (requireTokenOnPurchase) {
                throw new BusinessException(HotDealErrorCode.QUEUE_TOKEN_INVALID);
            }
            log.warn("Queue token mismatch tolerated by config: hotDealId={}, userId={}", hotDealId, userId);
        }
    }

    private HotDeal findActiveHotDeal(Long hotDealId) {
        HotDeal hotDeal = hotDealRepository.findById(hotDealId)
                .orElseThrow(() -> new BusinessException(HotDealErrorCode.HOT_DEAL_NOT_FOUND));
        if (hotDeal.getStatus() != HotDealStatus.ACTIVE) {
            throw new BusinessException(HotDealErrorCode.HOT_DEAL_NOT_ACTIVE);
        }
        return hotDeal;
    }

    private int resolveMaxPerUser(Long hotDealId, int fallbackMaxPerUser) {
        String maxPerUserValue = stringRedisTemplate.opsForValue().get(MAX_PER_USER_KEY_PREFIX + hotDealId);
        if (maxPerUserValue == null) {
            return fallbackMaxPerUser;
        }
        try {
            return Integer.parseInt(maxPerUserValue);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse hot-deal maxPerUser override. hotDealId={}, rawValue={}", hotDealId, maxPerUserValue);
            return fallbackMaxPerUser;
        }
    }
}
