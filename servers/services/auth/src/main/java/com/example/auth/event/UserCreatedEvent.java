package com.example.auth.event;

import com.example.event.DomainEvent;

import java.util.Map;

public class UserCreatedEvent extends DomainEvent {

    private final Long userId;
    private final String email;

    public UserCreatedEvent(Long userId, String email) {
        super("user-events");
        this.userId = userId;
        this.email = email;
    }

    @Override
    public String getEventTypeName() {
        return "UserCreated";
    }

    @Override
    public Map<String, Object> getPayload() {
        return Map.of(
                "userId", userId,
                "email", email
        );
    }
}
