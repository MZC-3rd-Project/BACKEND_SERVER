package com.example.profile.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.profile.dto.response.ProfileAddressResponse;
import com.example.profile.dto.response.ProfileResponse;
import com.example.profile.dto.response.internal.ProfileSnapshotResponse;
import com.example.profile.entity.Profiles;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag( name ="프로필 internal api", description = "profile 내부 통신")
public interface ProfileInternalAPI {

    @PostMapping("/batch")
    ApiResponse<List<Profiles>> findProfileList(
            @Valid @RequestBody List<@NotNull @Positive Long> userIdList
    );

    @GetMapping("/{userId}")
    ApiResponse<ProfileResponse> findProfile(@PathVariable("userId") Long userId);

    @GetMapping("/snapshot/{userId}")
    ApiResponse<ProfileSnapshotResponse> findProfileSnapshot(@PathVariable("userId") Long userId);

    @GetMapping("/profile_delivery/{userId}")
    ApiResponse<List<ProfileAddressResponse>> findDelivery(@PathVariable("userId") Long userId);
}
