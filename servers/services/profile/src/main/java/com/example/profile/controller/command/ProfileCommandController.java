package com.example.profile.controller.command;


import com.example.api.response.ApiResponse;
import com.example.profile.controller.api.command.ProfileCommandApi;
import com.example.profile.dto.request.ProfileImageRequest;
import com.example.profile.dto.response.ProfileImageResponse;
import com.example.profile.service.ProfileImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileCommandController implements ProfileCommandApi {
    private final ProfileImageService profileImageService;

    @Override
    public ApiResponse<ProfileImageResponse> createProfileImage(ProfileImageRequest req) {
        profileImageService.createProfileImage(req.getUserId(), req.getMediaId());
        return ApiResponse.success();
    }
}
