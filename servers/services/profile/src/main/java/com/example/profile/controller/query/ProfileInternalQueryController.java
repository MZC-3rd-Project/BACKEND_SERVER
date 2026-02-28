package com.example.profile.controller.query;

import com.example.api.response.ApiResponse;
import com.example.profile.controller.api.query.ProfileInternalAPI;
import com.example.profile.dto.response.ProfileAddressResponse;
import com.example.profile.dto.response.ProfileResponse;
import com.example.profile.entity.ProfileAddress;
import com.example.profile.entity.Profiles;
import com.example.profile.repository.ProfileAddressRepository;
import com.example.profile.service.query.ProfileQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/query/profile")
@RequiredArgsConstructor
public class ProfileInternalQueryController implements ProfileInternalAPI {

    private final ProfileQueryService profileQueryService;

    @Override
    public ApiResponse<List<Profiles>> findProfileList(List<Long> userIdList) {
       return ApiResponse.success(profileQueryService.getProfileList(userIdList));
    }

    @Override
    public ApiResponse<ProfileResponse> findProfile(Long userId) {
        return ApiResponse.success(profileQueryService.getProfile(userId));
    }

    @Override
    public ApiResponse<ProfileAddressResponse> findDelivery(Long userId) {
        return ApiResponse.success(profileQueryService.getProfileOfDeliveryAddress(userId));
    }
}
