package com.example.gateway.security.session.presentation;

import com.example.gateway.config.GatewaySessionProperties;
import com.example.gateway.security.session.application.GatewayRefreshTokenOpsService;
import com.example.gateway.security.session.domain.GatewayRefreshRotateRequest;
import com.example.gateway.security.session.domain.GatewayRefreshRotateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "gateway.session", name = {"enabled", "public-refresh-enabled"}, havingValue = "true")
public class GatewayPublicRefreshController {

    private static final String CODE_INVALID_REQUEST = "GW-REFRESH-400";
    private static final String CODE_REUSE_DETECTED = "GW-REFRESH-401";

    private final GatewayRefreshTokenOpsService refreshTokenOpsService;
    private final GatewaySessionProperties sessionProperties;

    @PostMapping("/refresh")
    public Mono<ResponseEntity<Map<String, Object>>> refresh(@RequestBody GatewayRefreshRotateRequest request,
                                                             ServerWebExchange exchange) {
        return refreshTokenOpsService.rotateRefreshTokenFamily(request)
                .flatMap(response -> {
                    if (isReuseDetected(response)) {
                        return invalidateBrowserSession(exchange)
                                .thenReturn(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(error(CODE_REUSE_DETECTED, "리프레시 토큰 재사용이 감지되어 세션이 종료되었습니다")));
                    }
                    return Mono.just(ResponseEntity.ok(success(response)));
                })
                .onErrorResume(IllegalArgumentException.class,
                        error -> Mono.just(ResponseEntity.badRequest()
                                .body(error(CODE_INVALID_REQUEST, error.getMessage()))));
    }

    private boolean isReuseDetected(GatewayRefreshRotateResponse response) {
        return "REUSE_DETECTED".equalsIgnoreCase(response.status());
    }

    private Mono<Void> invalidateBrowserSession(ServerWebExchange exchange) {
        return exchange.getSession()
                .flatMap(webSession -> webSession.invalidate()
                        .onErrorResume(error -> Mono.empty()))
                .then(Mono.fromRunnable(() -> {
                    String cookieName = sessionProperties.getSessionCookieName();
                    if (!StringUtils.hasText(cookieName)) {
                        return;
                    }
                    boolean secure = "https".equalsIgnoreCase(exchange.getRequest().getURI().getScheme());
                    ResponseCookie expiredCookie = ResponseCookie.from(cookieName, "")
                            .path(StringUtils.hasText(sessionProperties.getSessionCookiePath())
                                    ? sessionProperties.getSessionCookiePath()
                                    : "/")
                            .httpOnly(true)
                            .secure(secure)
                            .sameSite("Lax")
                            .maxAge(Duration.ZERO)
                            .build();
                    exchange.getResponse().addCookie(expiredCookie);
                }));
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
