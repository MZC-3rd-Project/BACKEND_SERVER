package com.example.profile.dto.response;

import com.example.profile.entity.ProfileAddress;
import com.example.profile.entity.Profiles;
import lombok.Builder;
import lombok.Getter;

import java.util.Optional;

@Getter
@Builder
public class ProfileResponse {

    private Long userId;
    private String phone;
    private String email;
    private String nickname;
    private Long mediaId;
    private String mediaUrl;
    private String defaultAddress;

    public static ProfileResponse from(Profiles profile, Optional<ProfileAddress> defaultAddress, String mediaUrl) {
        return ProfileResponse.builder()
            .userId(profile.getUserId())
            .phone(profile.getPhoneNumber())
            .email(profile.getEmail())
            .nickname(profile.getNickname())
            .mediaId(profile.getProfileImage() == null ? null : profile.getProfileImage().getMediaId())
            .mediaUrl(mediaUrl)
            .defaultAddress(defaultAddress.map(ProfileAddressResponse::buildFullAddress).orElse(null))
            .build();
    }
}
