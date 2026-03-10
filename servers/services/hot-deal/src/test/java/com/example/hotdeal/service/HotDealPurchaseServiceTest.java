package com.example.hotdeal.service;

import com.example.core.id.Snowflake;
import com.example.hotdeal.dto.HotDealPurchaseRequest;
import com.example.hotdeal.dto.HotDealPurchaseResponse;
import com.example.hotdeal.entity.HotDeal;
import com.example.hotdeal.entity.HotDealStatus;
import com.example.hotdeal.repository.HotDealRepository;
import com.example.hotdeal.service.checkout.HotDealCheckoutCommand;
import com.example.hotdeal.service.checkout.HotDealCheckoutProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotDealPurchaseServiceTest {

    @Mock
    private HotDealRepository hotDealRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private QueueService queueService;

    @Mock
    private HotDealCheckoutProcessor hotDealCheckoutProcessor;

    @Mock
    private Snowflake snowflake;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HotDeal hotDeal;

    private HotDealPurchaseService hotDealPurchaseService;

    @BeforeEach
    void setUp() {
        hotDealPurchaseService = new HotDealPurchaseService(
                hotDealRepository,
                stringRedisTemplate,
                queueService,
                hotDealCheckoutProcessor,
                snowflake
        );
    }

    @Test
    void purchase_delegatesToCheckoutProcessorWithResolvedCommand() {
        Long hotDealId = 100L;
        Long userId = 77L;
        Long orderId = 9001L;
        HotDealPurchaseRequest request = purchaseRequest(1, "queue-token");
        HotDealPurchaseResponse response = HotDealPurchaseResponse.success(
                orderId,
                LocalDateTime.of(2026, 3, 9, 12, 0)
        );

        mockPurchasableHotDeal(hotDealId, userId, 2);
        when(snowflake.nextId()).thenReturn(orderId);
        when(hotDealCheckoutProcessor.checkout(any(HotDealCheckoutCommand.class))).thenReturn(response);

        HotDealPurchaseResponse result = hotDealPurchaseService.purchase(hotDealId, request, userId);

        ArgumentCaptor<HotDealCheckoutCommand> commandCaptor = ArgumentCaptor.forClass(HotDealCheckoutCommand.class);
        verify(hotDealCheckoutProcessor).checkout(commandCaptor.capture());
        HotDealCheckoutCommand command = commandCaptor.getValue();

        assertThat(result).isSameAs(response);
        assertThat(command.hotDealId()).isEqualTo(hotDealId);
        assertThat(command.orderId()).isEqualTo(orderId);
        assertThat(command.itemId()).isEqualTo(501L);
        assertThat(command.userId()).isEqualTo(userId);
        assertThat(command.quantity()).isEqualTo(1);
        assertThat(command.discountedPrice()).isEqualTo(900L);
        assertThat(command.maxPerUser()).isEqualTo(2);
    }

    @Test
    void purchase_whenNotAdmitted_doesNotDelegateToCheckoutProcessor() {
        Long hotDealId = 101L;
        Long userId = 88L;
        HotDealPurchaseRequest request = purchaseRequest(1, "queue-token");

        when(queueService.isAdmitted(hotDealId, userId)).thenReturn(false);

        assertThatThrownBy(() -> hotDealPurchaseService.purchase(hotDealId, request, userId))
                .isInstanceOf(RuntimeException.class);

        verify(hotDealCheckoutProcessor, never()).checkout(any());
    }

    private void mockPurchasableHotDeal(Long hotDealId, Long userId, int maxPerUser) {
        String maxPerUserKey = "hotdeal:maxperuser:" + hotDealId;

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(queueService.isAdmitted(hotDealId, userId)).thenReturn(true);
        when(queueService.isTokenValid(hotDealId, userId, "queue-token")).thenReturn(true);
        when(hotDealRepository.findById(hotDealId)).thenReturn(Optional.of(hotDeal));
        when(hotDeal.getStatus()).thenReturn(HotDealStatus.ACTIVE);
        when(hotDeal.getMaxPerUser()).thenReturn(maxPerUser);
        when(hotDeal.getDiscountedPrice()).thenReturn(900L);
        when(hotDeal.getItemId()).thenReturn(501L);
        when(valueOperations.get(maxPerUserKey)).thenReturn(null);
    }

    private HotDealPurchaseRequest purchaseRequest(int quantity, String token) {
        HotDealPurchaseRequest request = new HotDealPurchaseRequest();
        ReflectionTestUtils.setField(request, "quantity", quantity);
        ReflectionTestUtils.setField(request, "token", token);
        return request;
    }
}
