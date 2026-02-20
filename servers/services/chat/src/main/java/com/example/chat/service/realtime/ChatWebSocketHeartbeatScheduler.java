package com.example.chat.service.realtime;

import com.example.chat.config.ChatRealtimeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHeartbeatScheduler {

    private final ChatWebSocketSessionRegistry sessionRegistry;
    private final ChatRealtimeProperties chatRealtimeProperties;
    private final ChatPresenceService chatPresenceService;

    @Scheduled(fixedDelayString = "${chat.realtime.heartbeat-check-interval-ms:10000}")
    public void closeIdleSessions() {
        long timeoutSeconds = Math.max(5L, chatRealtimeProperties.getHeartbeatTimeoutSeconds());
        Duration maxIdle = Duration.ofSeconds(timeoutSeconds);
        List<WebSocketSession> idleSessions = sessionRegistry.findIdleSessions(maxIdle);

        for (WebSocketSession session : idleSessions) {
            Long userId = sessionRegistry.getUserId(session);
            try {
                if (session.isOpen()) {
                    session.close(CloseStatus.SESSION_NOT_RELIABLE);
                }
            } catch (Exception e) {
                log.debug("Failed to close idle websocket session. sessionId={}", session.getId(), e);
            } finally {
                sessionRegistry.unregister(session);
                chatPresenceService.markDisconnected(userId);
            }
        }
    }
}
