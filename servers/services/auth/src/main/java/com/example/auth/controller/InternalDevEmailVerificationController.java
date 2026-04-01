package com.example.auth.controller;

import com.example.api.response.ApiResponse;
import com.example.auth.dto.response.DevEmailVerificationCodeResponse;
import com.example.auth.exception.AuthErrorCode;
import com.example.auth.service.DevEmailVerificationQueryService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Profile("develop")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/dev/email-verifications")
public class InternalDevEmailVerificationController {

    private final DevEmailVerificationQueryService devEmailVerificationQueryService;
    private final InternalAuthGuard internalAuthGuard;

    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<DevEmailVerificationCodeResponse>> getLatestVerificationCode(
            @RequestParam
            @NotBlank(message = "이메일은 필수입니다")
            @Email(message = "올바른 이메일 형식이 아닙니다")
            String email,
            @RequestHeader HttpHeaders headers
    ) {
        if (!internalAuthGuard.isAuthorized(headers)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(AuthErrorCode.INTERNAL_AUTH_FAILED));
        }

        DevEmailVerificationCodeResponse response = devEmailVerificationQueryService.getLatestVerificationCode(email);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
