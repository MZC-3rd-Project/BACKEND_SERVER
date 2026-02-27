package com.example.notification.service.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SseHeartbeatScheduler {

    private final SseConnectionManager sseConnectionManager;

    @Scheduled(fixedDelayString = "${notification.sse.heartbeat-ms:10000}")
    public void publishHeartbeat() {
        sseConnectionManager.publishHeartbeat();
    }
}
