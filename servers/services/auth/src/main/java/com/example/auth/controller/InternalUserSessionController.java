package com.example.auth.controller;

import com.example.api.response.ApiResponse;
import com.example.auth.dto.request.InternalUserSessionLogoutRequest;
import com.example.auth.exception.AuthErrorCode;
import com.example.auth.service.AuthService;
import com.example.security.gateway.GatewaySecurityModuleProperties;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/users/sessions")
public class InternalUserSessionController {

    private final AuthService authService;
    private final GatewaySecurityModuleProperties gatewaySecurityProperties;

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logoutUserSessions(
            @Valid @RequestBody InternalUserSessionLogoutRequest request,
            @RequestHeader HttpHeaders headers
    ) {
        if (!isInternalAuthorized(headers)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(AuthErrorCode.INTERNAL_AUTH_FAILED));
        }
        authService.logoutKeycloakUserSessions(request.userId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    private boolean isInternalAuthorized(HttpHeaders headers) {
        String authHeaderName = gatewaySecurityProperties.getInternalAuthHeader();
        String expectedToken = gatewaySecurityProperties.getInternalAuthToken();
        if (!StringUtils.hasText(authHeaderName) || !StringUtils.hasText(expectedToken)) {
            return false;
        }
        return expectedToken.equals(headers.getFirst(authHeaderName));
    }
}
