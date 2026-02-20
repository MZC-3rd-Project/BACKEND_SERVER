package com.example.chat.service.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChatWebSocketSessionRegistry {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionUsers = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> sessionRooms = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> roomSessions = new ConcurrentHashMap<>();

    public void register(WebSocketSession session, Long userId) {
        sessions.put(session.getId(), session);
        sessionUsers.put(session.getId(), userId);
        sessionRooms.put(session.getId(), ConcurrentHashMap.newKeySet());
    }

    public void unregister(WebSocketSession session) {
        String sessionId = session.getId();

        Set<Long> rooms = sessionRooms.remove(sessionId);
        if (rooms != null) {
            for (Long roomId : rooms) {
                Set<String> subscribers = roomSessions.get(roomId);
                if (subscribers != null) {
                    subscribers.remove(sessionId);
                    if (subscribers.isEmpty()) {
                        roomSessions.remove(roomId);
                    }
                }
            }
        }

        sessions.remove(sessionId);
        sessionUsers.remove(sessionId);
    }

    public void subscribeRoom(WebSocketSession session, Long roomId) {
        String sessionId = session.getId();

        sessionRooms.computeIfAbsent(sessionId, key -> ConcurrentHashMap.newKeySet()).add(roomId);
        roomSessions.computeIfAbsent(roomId, key -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    public Long getUserId(WebSocketSession session) {
        return sessionUsers.get(session.getId());
    }

    public Set<Long> getSubscribedRooms(WebSocketSession session) {
        return sessionRooms.getOrDefault(session.getId(), Collections.emptySet());
    }

    public void sendToSession(WebSocketSession session, String payload) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException e) {
            log.warn("Failed to send websocket message. sessionId={}", session.getId(), e);
            unregister(session);
        }
    }

    public void broadcastToRoom(Long roomId, String payload) {
        Set<String> subscribers = roomSessions.getOrDefault(roomId, Collections.emptySet());
        for (String sessionId : subscribers) {
            WebSocketSession session = sessions.get(sessionId);
            if (session != null) {
                sendToSession(session, payload);
            }
        }
    }
}
