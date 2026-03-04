package com.example.hotdeal.service;

import com.example.hotdeal.dto.QueueSseFanoutEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueSseRedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final QueueSseService queueSseService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            QueueSseFanoutEvent event = objectMapper.readValue(payload, QueueSseFanoutEvent.class);
            queueSseService.publishQueueStatus(
                    event.getHotDealId(),
                    event.getUserId(),
                    event.getPosition(),
                    event.isCanPurchase()
            );
        } catch (Exception e) {
            log.warn("Failed to consume hot-deal queue SSE redis message", e);
        }
    }
}
