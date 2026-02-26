package com.example.auth.controller;

import com.example.api.response.ApiResponse;
import com.example.auth.dto.request.ChangeEmailRequest;
import com.example.auth.dto.request.ChangePasswordRequest;
import com.example.auth.dto.request.SignupRequest;
import com.example.auth.dto.request.WithdrawRequest;
import com.example.auth.dto.response.SignupResponse;
import com.example.auth.service.AuthService;
import com.example.security.gateway.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ApiResponse.success(response);
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(
            @CurrentUserId Long userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userId, request);
        return ApiResponse.success();
    }

    @PutMapping("/email")
    public ApiResponse<Void> changeEmail(
            @CurrentUserId Long userId,
            @Valid @RequestBody ChangeEmailRequest request) {
        authService.changeEmail(userId, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/withdraw")
    public ApiResponse<Void> withdraw(
            @CurrentUserId Long userId,
            @Valid @RequestBody WithdrawRequest request) {
        authService.withdraw(userId, request);
        return ApiResponse.success();
    }

    @PostMapping("/login-record")
    public ApiResponse<Void> updateLastLogin(@CurrentUserId Long userId) {
        authService.updateLastLogin(userId);
        return ApiResponse.success();
    }
}
