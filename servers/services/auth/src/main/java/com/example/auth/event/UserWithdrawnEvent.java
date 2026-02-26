package com.example.auth.event;

import com.example.event.DomainEvent;

import java.util.Map;

public class UserWithdrawnEvent extends DomainEvent {

    private final Long userId;

    public UserWithdrawnEvent(Long userId) {
        super("user-events");
        this.userId = userId;
    }

    @Override
    public String getEventTypeName() {
        return "UserWithdrawn";
    }

    @Override
    public Map<String, Object> getPayload() {
        return Map.of("userId", userId);
    }
}
