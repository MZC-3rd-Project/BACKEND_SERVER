package com.example.profile.controller.command;


import com.example.api.response.ApiResponse;
import com.example.profile.controller.api.command.ProfileCommandApi;
import com.example.profile.dto.request.ProfileRequest;
import com.example.profile.dto.response.ProfileResponse;
import com.example.profile.service.command.ProfileCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileCommandController implements ProfileCommandApi {
    private final ProfileCommandService profileService;

    @Override
    public ApiResponse<ProfileResponse> createProfileImage(ProfileRequest req, Long userId) {
        profileService.updateProfile(req, userId);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> setDefaultAddress(Long addressId, Long userId) {
        profileService.setDefaultAddress(userId, addressId);
        return ApiResponse.success();
    }
}
