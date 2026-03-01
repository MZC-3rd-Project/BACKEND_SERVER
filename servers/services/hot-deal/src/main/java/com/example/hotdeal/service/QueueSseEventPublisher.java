package com.example.hotdeal.service;

import com.example.hotdeal.dto.QueueSseFanoutEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueSseEventPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final QueueSseService queueSseService;

    @Value("${hotdeal.queue.sse.topic:hotdeal-queue-sse-events}")
    private String topic;

    public void publishQueueStatus(Long hotDealId, Long userId, Long position, boolean canPurchase) {
        if (hotDealId == null || userId == null) {
            return;
        }
        publish(QueueSseFanoutEvent.builder()
                .hotDealId(hotDealId)
                .userId(userId)
                .position(position)
                .canPurchase(canPurchase)
                .build());
    }

    public void publishAdmittedUsers(Long hotDealId, Set<Long> admittedUserIds) {
        if (hotDealId == null || admittedUserIds == null || admittedUserIds.isEmpty()) {
            return;
        }
        admittedUserIds.forEach(userId -> publishQueueStatus(hotDealId, userId, 0L, true));
    }

    private void publish(QueueSseFanoutEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            stringRedisTemplate.convertAndSend(topic, payload);
        } catch (Exception e) {
            log.warn("Queue SSE redis publish failed. fallback local publish. topic={}, hotDealId={}, userId={}",
                    topic, event.getHotDealId(), event.getUserId(), e);
            queueSseService.publishQueueStatus(
                    event.getHotDealId(),
                    event.getUserId(),
                    event.getPosition(),
                    event.isCanPurchase()
            );
        }
    }
}
