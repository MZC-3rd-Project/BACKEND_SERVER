package com.example.gateway.security.session.presentation;

import com.example.gateway.config.GatewaySecurityProperties;
import com.example.gateway.security.session.application.GatewayRefreshTokenOpsService;
import com.example.gateway.security.session.domain.GatewayRefreshRotateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/gateway/sessions/refresh")
@ConditionalOnProperty(prefix = "gateway.session", name = {"enabled", "ops-enabled"}, havingValue = "true")
public class GatewayRefreshTokenOpsController {

    private static final String CODE_INTERNAL_UNAUTHORIZED = "GW-OPS-401";
    private static final String CODE_INVALID_REQUEST = "GW-OPS-400";

    private final GatewayRefreshTokenOpsService refreshTokenOpsService;
    private final GatewaySecurityProperties securityProperties;

    @PostMapping("/rotate")
    public Mono<ResponseEntity<Map<String, Object>>> rotateRefreshTokenFamily(@RequestBody GatewayRefreshRotateRequest request,
                                                                               ServerHttpRequest serverHttpRequest) {
        if (!isInternalAuthorized(serverHttpRequest.getHeaders())) {
            return Mono.just(unauthorized("내부 인증에 실패했습니다"));
        }
        return refreshTokenOpsService.rotateRefreshTokenFamily(request)
                .map(data -> ResponseEntity.ok(success(data)))
                .onErrorResume(IllegalArgumentException.class,
                        error -> Mono.just(badRequest(error.getMessage())));
    }

    private boolean isInternalAuthorized(HttpHeaders headers) {
        String authHeaderName = securityProperties.getInternalAuthHeader();
        String expectedToken = securityProperties.getInternalAuthToken();
        if (!StringUtils.hasText(authHeaderName) || !StringUtils.hasText(expectedToken)) {
            return false;
        }
        return expectedToken.equals(headers.getFirst(authHeaderName));
    }

    private ResponseEntity<Map<String, Object>> unauthorized(String message) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error(CODE_INTERNAL_UNAUTHORIZED, message));
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest().body(error(CODE_INVALID_REQUEST, message));
    }

    private Map<String, Object> success(Object data) {
        return Map.of(
                "success", true,
                "data", data
        );
    }

    private Map<String, Object> error(String code, String message) {
        return Map.of(
                "success", false,
                "error", Map.of(
                        "code", code,
                        "message", message
                )
        );
    }
}
