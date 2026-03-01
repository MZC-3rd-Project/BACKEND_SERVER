package com.example.hotdeal.service;

import com.example.core.exception.BusinessException;
import com.example.hotdeal.dto.QueueStatusResponse;
import com.example.hotdeal.dto.QueueStreamEventResponse;
import com.example.hotdeal.exception.HotDealErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueSseService {

    private static final String EVENT_CONNECTED = "queue-connected";
    private static final String EVENT_POSITION = "queue-position";
    private static final String EVENT_ADMITTED = "queue-admitted";
    private static final String EVENT_HEARTBEAT = "heartbeat";

    private final QueueService queueService;
    private final Map<String, QueueSession> sessions = new ConcurrentHashMap<>();

    @Value("${hotdeal.queue.sse.timeout-ms:600000}")
    private long emitterTimeoutMillis;

    public SseEmitter subscribe(Long hotDealId, Long userId) {
        SseEmitter emitter = new SseEmitter(emitterTimeoutMillis);
        QueueSession session = new QueueSession(hotDealId, userId, emitter);
        String sessionKey = sessionKey(hotDealId, userId);

        QueueSession previous = sessions.put(sessionKey, session);
        if (previous != null) {
            previous.emitter.complete();
        }

        emitter.onCompletion(() -> sessions.remove(sessionKey, session));
        emitter.onTimeout(() -> {
            sessions.remove(sessionKey, session);
            emitter.complete();
        });
        emitter.onError(error -> {
            sessions.remove(sessionKey, session);
            emitter.completeWithError(error);
        });

        sendEvent(session, EVENT_CONNECTED, null, false);
        publishLatestStatus(session);
        return emitter;
    }

    public void publishQueueStatus(Long hotDealId, Long userId, Long position, boolean canPurchase) {
        QueueSession session = sessions.get(sessionKey(hotDealId, userId));
        if (session == null) {
            return;
        }
        String eventName = canPurchase ? EVENT_ADMITTED : EVENT_POSITION;
        sendEvent(session, eventName, position, canPurchase);
    }

    @Scheduled(fixedDelayString = "${hotdeal.queue.sse.heartbeat-interval-ms:5000}")
    public void heartbeat() {
        sessions.values().forEach(this::refreshAndHeartbeat);
    }

    private void refreshAndHeartbeat(QueueSession session) {
        try {
            QueueStatusResponse status = queueService.getStatus(session.hotDealId, session.userId);
            if (isStateChanged(session, status.getPosition(), status.isCanPurchase())) {
                String eventName = status.isCanPurchase() ? EVENT_ADMITTED : EVENT_POSITION;
                sendEvent(session, eventName, status.getPosition(), status.isCanPurchase());
            }
            sendEvent(session, EVENT_HEARTBEAT, status.getPosition(), status.isCanPurchase());
        } catch (BusinessException e) {
            if (e.getErrorCode() == HotDealErrorCode.QUEUE_TOKEN_INVALID) {
                sendEvent(session, EVENT_HEARTBEAT, null, false);
                return;
            }
            unregister(session, e);
        } catch (Exception e) {
            unregister(session, e);
        }
    }

    private void publishLatestStatus(QueueSession session) {
        try {
            QueueStatusResponse status = queueService.getStatus(session.hotDealId, session.userId);
            String eventName = status.isCanPurchase() ? EVENT_ADMITTED : EVENT_POSITION;
            sendEvent(session, eventName, status.getPosition(), status.isCanPurchase());
        } catch (BusinessException e) {
            if (e.getErrorCode() != HotDealErrorCode.QUEUE_TOKEN_INVALID) {
                unregister(session, e);
            }
        } catch (Exception e) {
            unregister(session, e);
        }
    }

    private boolean isStateChanged(QueueSession session, Long position, boolean canPurchase) {
        return !Objects.equals(session.lastPosition, position)
                || !Objects.equals(session.lastCanPurchase, canPurchase);
    }

    private void sendEvent(QueueSession session, String eventName, Long position, boolean canPurchase) {
        QueueStreamEventResponse payload = QueueStreamEventResponse.builder()
                .hotDealId(session.hotDealId)
                .position(position)
                .canPurchase(canPurchase)
                .serverTime(Instant.now().toString())
                .build();

        try {
            session.emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(payload));
            session.lastPosition = position;
            session.lastCanPurchase = canPurchase;
        } catch (IOException e) {
            unregister(session, e);
        }
    }

    private void unregister(QueueSession session, Exception e) {
        String sessionKey = sessionKey(session.hotDealId, session.userId);
        sessions.remove(sessionKey, session);
        session.emitter.completeWithError(e);
        log.debug("Queue SSE session removed. hotDealId={}, userId={}, reason={}",
                session.hotDealId, session.userId, e.toString());
    }

    private String sessionKey(Long hotDealId, Long userId) {
        return hotDealId + ":" + userId;
    }

    private static final class QueueSession {
        private final Long hotDealId;
        private final Long userId;
        private final SseEmitter emitter;
        private volatile Long lastPosition;
        private volatile Boolean lastCanPurchase;

        private QueueSession(Long hotDealId, Long userId, SseEmitter emitter) {
            this.hotDealId = hotDealId;
            this.userId = userId;
            this.emitter = emitter;
        }
    }
}
