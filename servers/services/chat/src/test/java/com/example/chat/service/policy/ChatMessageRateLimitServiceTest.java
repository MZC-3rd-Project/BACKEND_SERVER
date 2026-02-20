package com.example.chat.service.policy;

import com.example.chat.config.ChatRateLimitProperties;
import com.example.chat.exception.ChatErrorCode;
import com.example.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageRateLimitServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ChatMessageRateLimitService chatMessageRateLimitService;

    @BeforeEach
    void setUp() {
        ChatRateLimitProperties properties = new ChatRateLimitProperties();
        properties.setPerSecond(8);
        properties.setPerMinute(100);
        properties.setKeyTtlSeconds(120);

        chatMessageRateLimitService = new ChatMessageRateLimitService(stringRedisTemplate, properties);
    }

    @Test
    void validateMessageSendRate_allowsWithinLimit() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L, 1L);
        when(stringRedisTemplate.expire(anyString(), any())).thenReturn(true);

        assertThatCode(() -> chatMessageRateLimitService.validateMessageSendRate(10L))
                .doesNotThrowAnyException();
    }

    @Test
    void validateMessageSendRate_throwsWhenExceeded() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(9L, 9L);
        when(stringRedisTemplate.expire(anyString(), any())).thenReturn(true);

        assertThatThrownBy(() -> chatMessageRateLimitService.validateMessageSendRate(10L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ChatErrorCode.MESSAGE_RATE_LIMIT_EXCEEDED);
    }

    @Test
    void validateMessageSendRate_fallbacksToLocalCounterWhenRedisFails() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("redis down"));

        for (int i = 0; i < 8; i++) {
            assertThatCode(() -> chatMessageRateLimitService.validateMessageSendRate(11L))
                    .doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> chatMessageRateLimitService.validateMessageSendRate(11L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ChatErrorCode.MESSAGE_RATE_LIMIT_EXCEEDED);
    }
}
