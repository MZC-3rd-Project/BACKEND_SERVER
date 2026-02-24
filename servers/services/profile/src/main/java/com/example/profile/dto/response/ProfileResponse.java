package com.example.profile.dto.response;

import com.example.profile.entity.Profiles;
import lombok.Builder;
import lombok.Getter;
import org.springframework.context.annotation.Profile;

import java.time.LocalDateTime;

@Getter
@Builder
public class ProfileResponse {

    private Long id;
    private Long userId;
    private String email;
    private String nickname;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProfileResponse from(Profiles profile) {
        return ProfileResponse.builder()
            .id(profile.getId())
            .userId(profile.getUserId())
            .email(profile.getEmail())
            .nickname(profile.getNickname())
            .createdAt(profile.getCreatedAt())
            .updatedAt(profile.getUpdatedAt())
            .build();
    }
}
