package com.example.profile.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.profile.dto.response.ProfileAddressResponse;
import com.example.profile.dto.response.ProfileResponse;
import com.example.profile.entity.ProfileAddress;
import com.example.profile.entity.Profiles;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag( name ="프로필 internal api", description = "profile 내부 통신")
public interface ProfileInternalAPI {

    @PostMapping("/batch")
    ApiResponse<List<Profiles>> findProfileList(@RequestBody List<Long> userIdList);

    @GetMapping("/{userId}")
    ApiResponse<ProfileResponse> findProfile(@PathVariable("userId") Long userId);

    @GetMapping("/profile_delivery/{userId}")
    ApiResponse<ProfileAddressResponse> findDelivery(@PathVariable("userId") Long userId);
}
