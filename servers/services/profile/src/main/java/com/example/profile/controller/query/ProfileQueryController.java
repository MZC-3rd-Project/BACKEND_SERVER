package com.example.profile.controller.query;


import com.example.api.response.ApiResponse;
import com.example.profile.controller.api.query.ProfileQueryAPI;
import com.example.profile.dto.response.AddressResponse;
import com.example.profile.dto.response.ProfileResponse;
import com.example.profile.service.query.ProfileQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/profile")
public class ProfileQueryController implements ProfileQueryAPI {
    private final ProfileQueryService profileQueryService;

    @Override
    public ApiResponse<ProfileResponse> getMyProfile(Long userId) {
        return ApiResponse.success(profileQueryService.getProfile(userId));
    }

    @Override
    public ApiResponse<List<AddressResponse>> getAddresses(Long userId) {
        return ApiResponse.success(profileQueryService.getAddresses(userId));
    }
}
