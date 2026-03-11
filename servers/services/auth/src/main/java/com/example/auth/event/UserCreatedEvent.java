package com.example.auth.event;

import com.example.event.DomainEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public class UserCreatedEvent extends DomainEvent {

    private final Long userId;
    private final String email;
    private final String nickname;

    public UserCreatedEvent(Long userId, String email, String nickname) {
        super("user-events");
        this.userId = userId;
        this.email = email;
        this.nickname = nickname;
    }

    @Override
    public String getEventTypeName() {
        return "UserCreated";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("email", email);
        payload.put("nickname", nickname);
        return payload;
    }
}
