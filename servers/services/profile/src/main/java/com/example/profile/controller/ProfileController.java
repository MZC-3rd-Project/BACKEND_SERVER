package com.example.profile.controller;

import com.example.api.response.ApiResponse;
import com.example.profile.dto.response.ProfileResponse;
import com.example.profile.dto.response.ProfilesImageResponse;
import com.example.profile.entity.Profiles;
import com.example.profile.repository.ProfilesImageRepository;
import com.example.profile.repository.ProfilesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.*;

import java.util.List;


@Slf4j
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {
    private final ProfilesRepository profilesRepository;
    private final ProfilesImageRepository profilesImageRepository;

    @GetMapping
    public ApiResponse<List<ProfileResponse>> getProfiles() {
        List<ProfileResponse> profiles = profilesRepository.findAll()
            .stream()
            .map(ProfileResponse::from)
            .toList();

        return ApiResponse.success(profiles);
    }
    @GetMapping("/profile_image")
    public ApiResponse<List<ProfilesImageResponse>> getProfileImage() {
        List<ProfilesImageResponse> result = profilesImageRepository.findAll()
            .stream()
            .map(ProfilesImageResponse::of)
            .toList();

        return ApiResponse.success(result);
    }

    @GetMapping("/create")
    public ApiResponse<ProfileResponse> createProfile() {
        Profiles result = profilesRepository.save(Profiles.builder()
            .email("@gmail.com")
            .nickname("heelow")
            .userId(10002L)
            .build());

        return ApiResponse.success(ProfileResponse.from(result));
    }

    @GetMapping("/{userId}")
    public ApiResponse<ProfileResponse> getProfile(@PathVariable Long userId) {
        Profiles profile = (Profiles) profilesRepository.findByUserId(userId)
            .orElseThrow(() -> new RuntimeException("프로필 없음"));
        return ApiResponse.success(ProfileResponse.from(profile));
    }
    @DeleteMapping("/{userId}")
    public ApiResponse deleteProfile(@PathVariable Long userId) {
        profilesRepository.deleteByUserId(userId);
        return ApiResponse.success();
    }

}
