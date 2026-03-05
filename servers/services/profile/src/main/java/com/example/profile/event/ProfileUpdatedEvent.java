package com.example.profile.event;

import com.example.event.DomainEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public class ProfileUpdatedEvent extends DomainEvent {

    private final Long profileId;
    private final Long userId;
    private final String email;
    private final String nickname;
    private final String phoneNumber;
    private final Long mediaId;

    public ProfileUpdatedEvent(
            Long profileId,
            Long userId,
            String email,
            String nickname,
            String phoneNumber,
            Long mediaId
    ) {
        super("profile-events");
        this.profileId = profileId;
        this.userId = userId;
        this.email = email;
        this.nickname = nickname;
        this.phoneNumber = phoneNumber;
        this.mediaId = mediaId;
    }

    @Override
    public String getEventTypeName() {
        return "ProfileUpdated";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("profileId", profileId);
        payload.put("userId", userId);
        payload.put("email", email);
        payload.put("nickname", nickname);
        payload.put("phoneNumber", phoneNumber);
        payload.put("mediaId", mediaId);
        return payload;
    }
}
