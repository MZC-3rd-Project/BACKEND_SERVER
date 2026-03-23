package com.example.profile.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.profile.dto.request.ProfileRequest;
import com.example.profile.dto.response.ProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Profile Command", description = "프로필 업데이트 / 배송지 메인 주소 설정")
public interface ProfileCommandApi {

    @Operation(summary = "프로필 업데이트")
    @PutMapping
    ApiResponse<ProfileResponse> createProfileImage(
        @RequestBody ProfileRequest req,
        @RequestHeader("X-User-Id") Long userId
    );

    @Operation(summary = "메인 배송지 설정")
    @PatchMapping("/addresses/{addressId}/default")
    ApiResponse<Void> setDefaultAddress(
        @PathVariable Long addressId,
        @RequestHeader("X-User-Id") Long userId
    );

}
