package com.example.auth.event;

import com.example.event.DomainEvent;

import java.util.Map;

public class UserEmailChangedEvent extends DomainEvent {

    private final Long userId;
    private final String oldEmail;
    private final String newEmail;

    public UserEmailChangedEvent(Long userId, String oldEmail, String newEmail) {
        super("user-events");
        this.userId = userId;
        this.oldEmail = oldEmail;
        this.newEmail = newEmail;
    }

    @Override
    public String getEventTypeName() {
        return "UserEmailChanged";
    }

    @Override
    public Map<String, Object> getPayload() {
        return Map.of(
                "userId", userId,
                "oldEmail", oldEmail,
                "newEmail", newEmail
        );
    }
}
