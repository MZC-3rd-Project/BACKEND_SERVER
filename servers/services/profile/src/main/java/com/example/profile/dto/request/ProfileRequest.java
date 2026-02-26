package com.example.profile.dto.request;

import com.example.core.id.jackson.SnowflakeId;
import com.example.profile.entity.Profiles;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ProfileRequest {

    private Long id;
    @SnowflakeId
    private Long userId;
    @SnowflakeId
    private Long mediaId;
    private String email;
    private String phone;
    private String delevery;
    private String nickname;

    public static ProfileRequest from(Profiles profile) {
        return ProfileRequest.builder()
            .email(profile.getEmail())
            .phone(profile.getPhone())
            .delevery(profile.getDelivery())
            .nickname(profile.getNickname())
            .build();
    }
}
