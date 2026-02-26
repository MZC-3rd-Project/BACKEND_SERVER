package com.example.profile.controller.query;


import com.example.api.response.ApiResponse;
import com.example.profile.controller.api.query.ProfileQueryAPI;
import com.example.profile.dto.response.ProfileResponse;
import com.example.profile.service.query.ProfileQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/profile")
public class ProfileQueryController implements ProfileQueryAPI {
    private final ProfileQueryService profileQueryService;

    @Override
    public ApiResponse<ProfileResponse> getMyProfile(Long userId) {
        ProfileResponse response = profileQueryService.getProfile(userId);

        return ApiResponse.success(response);
    }
}
