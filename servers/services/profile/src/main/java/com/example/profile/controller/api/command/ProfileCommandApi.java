package com.example.profile.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.profile.dto.request.ProfileRequest;
import com.example.profile.dto.response.ProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Profile Image", description = "profile image 조회")
public interface ProfileCommandApi {

    @Operation(summary = "프로필 업데이트")
    @PatchMapping
    ApiResponse<ProfileResponse> createProfileImage(
        @RequestBody ProfileRequest req);
}
