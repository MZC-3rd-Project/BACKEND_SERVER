package com.example.profile.dto.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.profile.dto.request.ProfileRequest;
import com.example.profile.entity.Profiles;
import lombok.Builder;
import lombok.Getter;
import org.springframework.context.annotation.Profile;

import java.time.LocalDateTime;

@Getter
@Builder
public class ProfileResponse {

    private Long id;
    @SnowflakeId
    private Long userId;
    @SnowflakeId
    private Long mediaId;
    private String email;
    private String nickname;

    public static ProfileResponse from(ProfileRequest profile) {
        return ProfileResponse.builder()
            .id(profile.getId())
            .userId(profile.getUserId())
            .email(profile.getEmail())
            .nickname(profile.getNickname())
            .build();
    }
}
