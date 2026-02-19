package com.example.notification.service.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseEventPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final SseConnectionManager sseConnectionManager;

    @Value("${notification.sse.topic:sse-notifications}")
    private String sseTopic;

    public void publish(SseNotificationEvent event) {
        if (event == null || event.getUserId() == null) {
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(event);
            stringRedisTemplate.convertAndSend(sseTopic, payload);
        } catch (Exception e) {
            log.warn("SSE redis publish failed. fallback local publish. topic={}, userId={}",
                    sseTopic, event.getUserId(), e);
            sseConnectionManager.publishToUser(event);
        }
    }
}
