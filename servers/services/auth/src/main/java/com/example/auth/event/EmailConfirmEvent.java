package com.example.auth.event;

import com.example.event.DomainEvent;

import java.util.Map;

public class EmailConfirmEvent extends DomainEvent {

    private final String email;
    private final String verificationCode;

    public EmailConfirmEvent(String email, String verificationCode) {
        super("auth-events");
        this.email = email;
        this.verificationCode = verificationCode;
    }

    @Override
    public String getEventTypeName() {
        return "EMAIL_CONFIRM_EVENT";
    }

    @Override
    public Map<String, Object> getPayload() {
        return Map.of(
                "email", email,
                "verificationCode", verificationCode
        );
    }
}
