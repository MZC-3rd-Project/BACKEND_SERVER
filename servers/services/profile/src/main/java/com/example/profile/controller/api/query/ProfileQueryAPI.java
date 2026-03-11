package com.example.profile.controller.api.query;


import com.example.api.response.ApiResponse;
import com.example.profile.dto.response.ProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag( name ="프로필 조회 api", description = "프로필 접속 시 자신의 프로필 정보를 제공")
public interface ProfileQueryAPI {
    @Operation(summary = "프로필 조회")
    @GetMapping
    ApiResponse<ProfileResponse> getMyProfile(
        @RequestHeader("X-User-Id") Long userId);
}
