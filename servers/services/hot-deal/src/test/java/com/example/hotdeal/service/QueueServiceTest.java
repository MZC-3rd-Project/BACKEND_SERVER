package com.example.hotdeal.service;

import com.example.hotdeal.dto.QueueEnterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

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

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private QueueService queueService;

    @BeforeEach
    void setUp() {
        queueService = new QueueService(redisTemplate);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
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
}
