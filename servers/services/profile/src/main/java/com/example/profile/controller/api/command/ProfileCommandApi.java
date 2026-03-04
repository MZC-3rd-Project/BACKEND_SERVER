package com.example.profile.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.profile.dto.request.ProfileRequest;
import com.example.profile.dto.response.ProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Profile Image", description = "profile 업데이트 및 이미지 삭제(mediaId == null -> 삭제)")
public interface ProfileCommandApi {

    @Operation(summary = "프로필 업데이트")
    @PutMapping
    ApiResponse<ProfileResponse> createProfileImage(
        @RequestBody ProfileRequest req,
        @RequestHeader("X-User-Id") Long userId
    );

}
