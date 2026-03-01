package com.example.hotdeal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueSseEventPublisherTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private QueueSseService queueSseService;

    @InjectMocks
    private QueueSseEventPublisher queueSseEventPublisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(queueSseEventPublisher, "topic", "hotdeal-queue-sse-events");
    }

    @Test
    void publishQueueStatus_publishesRedisMessage() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"ok\":true}");

        queueSseEventPublisher.publishQueueStatus(100L, 77L, 3L, false);

        verify(stringRedisTemplate).convertAndSend("hotdeal-queue-sse-events", "{\"ok\":true}");
        verify(queueSseService, never()).publishQueueStatus(any(), any(), any(), anyBoolean());
    }

    @Test
    void publishQueueStatus_fallsBackToLocalPublishWhenRedisFails() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("redis-down"));

        queueSseEventPublisher.publishQueueStatus(100L, 77L, 3L, false);

        verify(queueSseService).publishQueueStatus(100L, 77L, 3L, false);
    }
}
