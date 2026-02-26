package com.example.profile.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.profile.dto.request.ProfileImageRequest;
import com.example.profile.dto.response.ProfileImageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Profile Image", description = "profile image 조회")
public interface ProfileCommandApi {
    /*
    * dto
    * {
    *   userid
    *   media_id
    * }
    * */
    @Operation(summary = "프로필 생성")
    @PatchMapping("/profile_image")
    ApiResponse<ProfileImageResponse> createProfileImage(
        @RequestBody ProfileImageRequest req);
}
