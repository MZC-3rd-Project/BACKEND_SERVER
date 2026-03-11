package com.example.hotdeal.service;

import com.example.hotdeal.dto.QueueEnterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ZSetOperations<String, String> stringZSetOperations;

    @Mock
    private ValueOperations<String, String> stringValueOperations;

    private QueueService queueService;

    @BeforeEach
    void setUp() {
        queueService = new QueueService(redisTemplate, stringRedisTemplate);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(stringRedisTemplate.opsForZSet()).thenReturn(stringZSetOperations);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
    }

    @Test
    void enter_whenAlreadyQueued_returnsExistingPositionAndToken() {
        Long hotDealId = 100L;
        Long userId = 11L;
        String queueKey = "hotdeal:queue:100";
        String tokenKey = "hotdeal:token:100:11";

        when(valueOperations.get(tokenKey)).thenReturn("existing-token");
        when(zSetOperations.rank(queueKey, "11")).thenReturn(4L);

        QueueEnterResponse response = queueService.enter(hotDealId, userId);

        assertThat(response.getToken()).isEqualTo("existing-token");
        assertThat(response.getPosition()).isEqualTo(5L);
        assertThat(response.getEstimatedWaitSeconds()).isEqualTo(10L);
        verify(zSetOperations, never()).add(anyString(), any(), anyDouble());
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    void enter_whenAlreadyAdmitted_returnsImmediateResponse() {
        Long hotDealId = 101L;
        Long userId = 12L;
        String queueKey = "hotdeal:queue:101";
        String tokenKey = "hotdeal:token:101:12";
        String admittedKey = "hotdeal:admitted:101:12";

        when(valueOperations.get(tokenKey)).thenReturn("admitted-token");
        when(zSetOperations.rank(queueKey, "12")).thenReturn(null);
        when(redisTemplate.hasKey(admittedKey)).thenReturn(true);

        QueueEnterResponse response = queueService.enter(hotDealId, userId);

        assertThat(response.getToken()).isEqualTo("admitted-token");
        assertThat(response.getPosition()).isEqualTo(0L);
        assertThat(response.getEstimatedWaitSeconds()).isEqualTo(0L);
        verify(zSetOperations, never()).add(anyString(), any(), anyDouble());
    }

    @Test
    void enter_whenNewUser_addsQueueAndIssuesToken() {
        Long hotDealId = 102L;
        Long userId = 13L;
        String queueKey = "hotdeal:queue:102";
        String tokenKey = "hotdeal:token:102:13";
        String admittedKey = "hotdeal:admitted:102:13";

        when(valueOperations.get(tokenKey)).thenReturn(null);
        when(zSetOperations.rank(queueKey, "13")).thenReturn(null, 0L);
        when(redisTemplate.hasKey(admittedKey)).thenReturn(false);

        QueueEnterResponse response = queueService.enter(hotDealId, userId);

        assertThat(response.getPosition()).isEqualTo(1L);
        assertThat(response.getEstimatedWaitSeconds()).isEqualTo(2L);
        assertThat(response.getToken()).isNotBlank();
        verify(zSetOperations).add(eq(queueKey), eq("13"), anyDouble());
        verify(valueOperations).set(eq(tokenKey), anyString(), eq(30L), eq(TimeUnit.MINUTES));
    }

    @Test
    void enter_whenQueuedButTokenExpired_reissuesTokenWithoutRequeue() {
        Long hotDealId = 103L;
        Long userId = 14L;
        String queueKey = "hotdeal:queue:103";
        String tokenKey = "hotdeal:token:103:14";

        when(valueOperations.get(tokenKey)).thenReturn(null);
        when(zSetOperations.rank(queueKey, "14")).thenReturn(2L);

        QueueEnterResponse response = queueService.enter(hotDealId, userId);

        assertThat(response.getPosition()).isEqualTo(3L);
        assertThat(response.getEstimatedWaitSeconds()).isEqualTo(6L);
        assertThat(response.getToken()).isNotBlank();
        verify(zSetOperations, never()).add(anyString(), any(), anyDouble());

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(tokenKey), tokenCaptor.capture(), anyLong(), eq(TimeUnit.MINUTES));
        assertThat(tokenCaptor.getValue()).isNotBlank();
    }

    @Test
    void admitUsersByAvailableStock_admitsOnlyAdditionalUsersNeededWithinConcurrencyLimit() {
        Long hotDealId = 104L;
        String queueKey = "hotdeal:queue:104";
        String stockKey = "hotdeal:stock:104";
        String slotKey = "hotdeal:admitted:slots:104";

        when(stringValueOperations.get(stockKey)).thenReturn("5");
        when(stringZSetOperations.zCard(slotKey)).thenReturn(2L);
        when(zSetOperations.popMin(queueKey, 2)).thenReturn(Set.of(
                new DefaultTypedTuple<>("21", 1d),
                new DefaultTypedTuple<>("22", 2d)
        ));

        Set<Long> admittedUsers = queueService.admitUsersByAvailableStock(hotDealId, 4);

        assertThat(admittedUsers).containsExactlyInAnyOrder(21L, 22L);
        verify(stringZSetOperations).removeRangeByScore(eq(slotKey), eq(Double.NEGATIVE_INFINITY), anyDouble());
        verify(valueOperations).set("hotdeal:admitted:104:21", "true", 10L, TimeUnit.MINUTES);
        verify(valueOperations).set("hotdeal:admitted:104:22", "true", 10L, TimeUnit.MINUTES);
        verify(stringZSetOperations).add(eq(slotKey), eq("21"), anyDouble());
        verify(stringZSetOperations).add(eq(slotKey), eq("22"), anyDouble());
    }

    @Test
    void admitUsersByAvailableStock_usesStockAsUpperBoundWhenStockIsLowerThanConcurrencyLimit() {
        Long hotDealId = 106L;
        String queueKey = "hotdeal:queue:106";
        String stockKey = "hotdeal:stock:106";
        String slotKey = "hotdeal:admitted:slots:106";

        when(stringValueOperations.get(stockKey)).thenReturn("2");
        when(stringZSetOperations.zCard(slotKey)).thenReturn(0L);
        when(zSetOperations.popMin(queueKey, 2)).thenReturn(Set.of(
                new DefaultTypedTuple<>("41", 1d),
                new DefaultTypedTuple<>("42", 2d)
        ));

        Set<Long> admittedUsers = queueService.admitUsersByAvailableStock(hotDealId, 10);

        assertThat(admittedUsers).containsExactlyInAnyOrder(41L, 42L);
    }

    @Test
    void releaseAdmissionSlot_removesUserFromSlotIndex() {
        queueService.releaseAdmissionSlot(105L, 31L);

        verify(stringZSetOperations).remove("hotdeal:admitted:slots:105", "31");
    }
}
