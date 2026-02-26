package com.example.profile.dto.request;

import com.example.core.id.jackson.SnowflakeId;
import com.example.profile.entity.Profiles;
import lombok.Builder;
import lombok.Getter;

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
    private String delivery;
    private String nickname;

    public static ProfileRequest from(Profiles profile) {
        return ProfileRequest.builder()
            .email(profile.getEmail())
            .phone(profile.getPhone())
            .delivery(profile.getDelivery())
            .nickname(profile.getNickname())
            .build();
    }
}
