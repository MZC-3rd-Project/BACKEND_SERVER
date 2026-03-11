package com.example.hotdeal.service.checkout;

import com.example.event.DomainEvent;
import com.example.event.EventPublisher;
import com.example.hotdeal.dto.HotDealPurchaseResponse;
import com.example.hotdeal.event.HotDealPurchasedEvent;
import com.example.hotdeal.repository.HotDealRepository;
import com.example.hotdeal.service.QueueService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyRedisHotDealCheckoutProcessorTest {

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

    private LegacyRedisHotDealCheckoutProcessor checkoutProcessor;

    @BeforeEach
    void setUp() {
        checkoutProcessor = new LegacyRedisHotDealCheckoutProcessor(
                hotDealRepository,
                eventPublisher,
                stringRedisTemplate,
                queueService
        );
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void checkout_whenUserReachedMaxPerUser_releasesAdmissionSlot() {
        HotDealCheckoutCommand command = command(100L, 9001L, 77L, 1, 900L, 1);

        mockSuccessfulCheckout(command, 1);

        HotDealPurchaseResponse response = checkoutProcessor.checkout(command);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getOrderId()).isEqualTo(9001L);
        verify(queueService).releaseAdmissionSlot(100L, 77L);

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture(), any());

        HotDealPurchasedEvent event = Assertions.assertInstanceOf(HotDealPurchasedEvent.class, eventCaptor.getValue());
        assertThat(event.getOrderId()).isEqualTo(9001L);
        assertThat(event.getPayload()).containsEntry("orderId", 9001L);
    }

    @Test
    void checkout_whenUserCanStillBuy_keepsAdmissionSlot() {
        HotDealCheckoutCommand command = command(101L, 9002L, 88L, 1, 900L, 2);

        mockSuccessfulCheckout(command, 1);

        checkoutProcessor.checkout(command);

        verify(queueService, never()).releaseAdmissionSlot(101L, 88L);
    }

    private void mockSuccessfulCheckout(HotDealCheckoutCommand command, int purchasedQuantity) {
        String purchasedKey = "hotdeal:purchased:" + command.hotDealId() + ":" + command.userId();

        when(hotDealRepository.incrementSoldQuantity(command.hotDealId(), command.quantity())).thenReturn(1);
        when(stringRedisTemplate.execute(
                org.mockito.ArgumentMatchers.<DefaultRedisScript<Long>>any(),
                anyList(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(1L);
        when(valueOperations.get(purchasedKey)).thenReturn(String.valueOf(purchasedQuantity));
    }

    private HotDealCheckoutCommand command(Long hotDealId, Long orderId, Long userId,
                                           int quantity, Long discountedPrice, int maxPerUser) {
        return new HotDealCheckoutCommand(
                hotDealId,
                orderId,
                501L,
                userId,
                quantity,
                discountedPrice,
                maxPerUser
        );
    }
}
