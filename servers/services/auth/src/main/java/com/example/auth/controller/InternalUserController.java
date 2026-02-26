package com.example.auth.controller;

import com.example.api.response.ApiResponse;
import com.example.auth.dto.response.UserExistsResponse;
import com.example.auth.dto.response.UserInfoResponse;
import com.example.auth.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserQueryService userQueryService;

    @GetMapping("/{userId}")
    public ApiResponse<UserInfoResponse> getUserInfo(@PathVariable Long userId) {
        UserInfoResponse response = userQueryService.getUserInfo(userId);
        return ApiResponse.success(response);
    }

    @GetMapping("/{userId}/exists")
    public ApiResponse<UserExistsResponse> checkUserExists(@PathVariable Long userId) {
        UserExistsResponse response = userQueryService.checkUserExists(userId);
        return ApiResponse.success(response);
    }

    @GetMapping("/by-keycloak/{keycloakId}")
    public ApiResponse<UserInfoResponse> getUserByKeycloakId(@PathVariable String keycloakId) {
        UserInfoResponse response = userQueryService.getUserByKeycloakId(keycloakId);
        return ApiResponse.success(response);
    }
}
