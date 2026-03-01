package com.example.hotdeal.service;

import com.example.hotdeal.dto.QueueSseFanoutEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueSseRedisSubscriberTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private QueueSseService queueSseService;

    @InjectMocks
    private QueueSseRedisSubscriber queueSseRedisSubscriber;

    @Test
    void onMessage_forwardsFanoutEventToLocalSseService() throws Exception {
        String json = "{\"hotDealId\":100,\"userId\":77,\"position\":2,\"canPurchase\":false}";
        Message message = new DefaultMessage(
                json.getBytes(StandardCharsets.UTF_8),
                "hotdeal-queue-sse-events".getBytes(StandardCharsets.UTF_8)
        );
        QueueSseFanoutEvent event = QueueSseFanoutEvent.builder()
                .hotDealId(100L)
                .userId(77L)
                .position(2L)
                .canPurchase(false)
                .build();
        when(objectMapper.readValue(anyString(), eq(QueueSseFanoutEvent.class))).thenReturn(event);

        queueSseRedisSubscriber.onMessage(message, null);

        verify(queueSseService).publishQueueStatus(100L, 77L, 2L, false);
    }
}
