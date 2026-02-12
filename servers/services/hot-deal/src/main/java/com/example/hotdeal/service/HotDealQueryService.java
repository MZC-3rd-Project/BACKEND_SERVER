package com.example.hotdeal.service;

import com.example.core.exception.BusinessException;
import com.example.hotdeal.dto.HotDealDetailResponse;
import com.example.hotdeal.dto.HotDealListResponse;
import com.example.hotdeal.entity.HotDeal;
import com.example.hotdeal.entity.HotDealStatus;
import com.example.hotdeal.exception.HotDealErrorCode;
import com.example.hotdeal.repository.HotDealRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HotDealQueryService {

    private final HotDealRepository hotDealRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_KEY_PREFIX = "hotdeal:detail:";
    private static final long CACHE_TTL_SECONDS = 10;

    public List<HotDealListResponse> getActiveDeals(Long cursor, int size) {
        if (cursor == null || cursor == 0) {
            cursor = Long.MAX_VALUE;
        }

        return hotDealRepository.findByStatusWithCursor(
                        HotDealStatus.ACTIVE, cursor, PageRequest.of(0, size))
                .stream()
                .map(HotDealListResponse::from)
                .toList();
    }

    public HotDealDetailResponse getDetail(Long hotDealId) {
        String cacheKey = CACHE_KEY_PREFIX + hotDealId;

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof HotDealDetailResponse response) {
            return response;
        }

        HotDeal hotDeal = hotDealRepository.findById(hotDealId)
                .orElseThrow(() -> new BusinessException(HotDealErrorCode.HOT_DEAL_NOT_FOUND));

        HotDealDetailResponse response = HotDealDetailResponse.from(hotDeal);

        redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL_SECONDS, TimeUnit.SECONDS);

        return response;
    }
}
