package com.example.profile.event;

import com.example.event.DomainEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public class ProfileCreatedEvent extends DomainEvent {

    private final Long profileId;
    private final Long userId;
    private final String email;
    private final String nickname;

    public ProfileCreatedEvent(Long profileId, Long userId, String email, String nickname) {
        super("profile-events");
        this.profileId = profileId;
        this.userId = userId;
        this.email = email;
        this.nickname = nickname;
    }

    @Override
    public String getEventTypeName() {
        return "ProfileCreated";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("profileId", profileId);
        payload.put("userId", userId);
        payload.put("email", email);
        payload.put("nickname", nickname);
        return payload;
    }
}
