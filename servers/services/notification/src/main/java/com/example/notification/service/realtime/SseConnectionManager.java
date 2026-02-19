package com.example.notification.service.realtime;

import com.example.core.exception.BusinessException;
import com.example.notification.config.NotificationSseProperties;
import com.example.notification.exception.NotificationErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseConnectionManager {

    private static final long SSE_TIMEOUT_MS = 30L * 60L * 1000L;
    private static final String CONNECT_EVENT = "connected";
    private static final String NOTIFICATION_EVENT = "notification";
    private static final String HEARTBEAT_EVENT = "heartbeat";

    private final NotificationSseProperties sseProperties;
    private final Map<Long, Map<String, SseEmitter>> userEmitters = new ConcurrentHashMap<>();
    private final AtomicInteger totalConnections = new AtomicInteger(0);

    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String connectionId = buildConnectionId(userId);
        Map<String, SseEmitter> emitters = userEmitters.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>());

        synchronized (this) {
            int maxPerUser = Math.max(1, sseProperties.getMaxConnectionsPerUser());
            int maxTotal = Math.max(1, sseProperties.getMaxTotalConnections());

            if (emitters.size() >= maxPerUser || totalConnections.get() >= maxTotal) {
                throw new BusinessException(NotificationErrorCode.SSE_CONNECTION_LIMIT_EXCEEDED);
            }
            emitters.put(connectionId, emitter);
            totalConnections.incrementAndGet();
        }

        emitter.onCompletion(() -> disconnect(userId, connectionId));
        emitter.onTimeout(() -> disconnect(userId, connectionId));
        emitter.onError(ignored -> disconnect(userId, connectionId));

        sendEvent(userId, connectionId, emitter, CONNECT_EVENT, Map.of(
                "connectionId", connectionId,
                "connectedAt", LocalDateTime.now().toString()
        ));
        return emitter;
    }

    public void publishToUser(SseNotificationEvent event) {
        if (event == null || event.getUserId() == null) {
            return;
        }
        Map<String, SseEmitter> emitters = userEmitters.get(event.getUserId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        emitters.forEach((connectionId, emitter) ->
                sendEvent(event.getUserId(), connectionId, emitter, NOTIFICATION_EVENT, event));
    }

    public void publishHeartbeat() {
        if (userEmitters.isEmpty()) {
            return;
        }
        userEmitters.forEach((userId, emitters) -> emitters.forEach((connectionId, emitter) ->
                sendEvent(userId, connectionId, emitter, HEARTBEAT_EVENT, LocalDateTime.now().toString())));
    }

    public int connectionCount(Long userId) {
        Map<String, SseEmitter> emitters = userEmitters.get(userId);
        return emitters == null ? 0 : emitters.size();
    }

    public int totalConnectionCount() {
        return totalConnections.get();
    }

    private void sendEvent(Long userId, String connectionId, SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .id(connectionId)
                    .name(eventName)
                    .data(data));
        } catch (IOException e) {
            log.debug("SSE send failed. userId={}, connectionId={}, event={}", userId, connectionId, eventName, e);
            disconnect(userId, connectionId);
        }
    }

    private void disconnect(Long userId, String connectionId) {
        Map<String, SseEmitter> emitters = userEmitters.get(userId);
        if (emitters == null) {
            return;
        }
        SseEmitter removed = emitters.remove(connectionId);
        if (removed != null) {
            totalConnections.updateAndGet(value -> value > 0 ? value - 1 : 0);
        }
        if (emitters.isEmpty()) {
            userEmitters.remove(userId);
        }
    }

    private String buildConnectionId(Long userId) {
        return userId + ":" + UUID.randomUUID();
    }
}
