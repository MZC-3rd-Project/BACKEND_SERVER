package com.example.hotdeal.service;

import com.example.event.EventPublisher;
import com.example.hotdeal.dto.HotDealPurchaseRequest;
import com.example.hotdeal.entity.HotDeal;
import com.example.hotdeal.entity.HotDealStatus;
import com.example.hotdeal.repository.HotDealRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotDealPurchaseServiceTest {

    @Mock
    private HotDealRepository hotDealRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private QueueService queueService;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HotDeal hotDeal;

    private HotDealPurchaseService hotDealPurchaseService;

    @BeforeEach
    void setUp() {
        hotDealPurchaseService = new HotDealPurchaseService(
                hotDealRepository,
                eventPublisher,
                stringRedisTemplate,
                queueService
        );
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void purchase_whenUserReachedMaxPerUser_releasesAdmissionSlot() {
        Long hotDealId = 100L;
        Long userId = 77L;
        HotDealPurchaseRequest request = purchaseRequest(1, "queue-token");

        mockSuccessfulPurchase(hotDealId, userId, 1, 1);

        hotDealPurchaseService.purchase(hotDealId, request, userId);

        verify(queueService).releaseAdmissionSlot(hotDealId, userId);
    }

    @Test
    void purchase_whenUserCanStillBuy_keepsAdmissionSlot() {
        Long hotDealId = 101L;
        Long userId = 88L;
        HotDealPurchaseRequest request = purchaseRequest(1, "queue-token");

        mockSuccessfulPurchase(hotDealId, userId, 2, 1);

        hotDealPurchaseService.purchase(hotDealId, request, userId);

        verify(queueService, never()).releaseAdmissionSlot(hotDealId, userId);
    }

    private void mockSuccessfulPurchase(Long hotDealId, Long userId, int maxPerUser, int purchasedQuantity) {
        String maxPerUserKey = "hotdeal:maxperuser:" + hotDealId;
        String purchasedKey = "hotdeal:purchased:" + hotDealId + ":" + userId;

        when(queueService.isAdmitted(hotDealId, userId)).thenReturn(true);
        when(queueService.isTokenValid(hotDealId, userId, "queue-token")).thenReturn(true);
        when(hotDealRepository.findById(hotDealId)).thenReturn(Optional.of(hotDeal));
        when(hotDeal.getStatus()).thenReturn(HotDealStatus.ACTIVE);
        when(hotDeal.getMaxPerUser()).thenReturn(maxPerUser);
        when(hotDeal.getDiscountedPrice()).thenReturn(900L);
        when(hotDeal.getItemId()).thenReturn(501L);
        when(hotDealRepository.incrementSoldQuantity(hotDealId, 1)).thenReturn(1);
        when(stringRedisTemplate.execute(
                org.mockito.ArgumentMatchers.<DefaultRedisScript<Long>>any(),
                anyList(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(1L);
        when(valueOperations.get(maxPerUserKey)).thenReturn(null);
        when(valueOperations.get(purchasedKey)).thenReturn(String.valueOf(purchasedQuantity));
    }

    private HotDealPurchaseRequest purchaseRequest(int quantity, String token) {
        HotDealPurchaseRequest request = new HotDealPurchaseRequest();
        ReflectionTestUtils.setField(request, "quantity", quantity);
        ReflectionTestUtils.setField(request, "token", token);
        return request;
    }
}
