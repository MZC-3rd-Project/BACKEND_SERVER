package com.example.profile.dto.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.profile.entity.Profiles;
import lombok.Builder;
import lombok.Getter;


@Getter
@Builder
public class ProfileResponse {

    private String phone;
    private String email;
    private String delivery;
    private String nickname;

    public static ProfileResponse from(Profiles profile) {
        return ProfileResponse.builder()
            .phone(profile.getPhone())
            .delivery(profile.getDelivery())
            .email(profile.getEmail())
            .nickname(profile.getNickname())
            .build();
    }
}
