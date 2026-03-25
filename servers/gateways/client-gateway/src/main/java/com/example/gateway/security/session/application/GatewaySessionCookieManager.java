package com.example.gateway.security.session.application;

import com.example.gateway.config.GatewaySessionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class GatewaySessionCookieManager {

    private final GatewaySessionProperties sessionProperties;

    public String extractSessionId(ServerWebExchange exchange) {
        String cookieName = sessionProperties.getSessionCookieName();
        if (!StringUtils.hasText(cookieName)) {
            return null;
        }
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(cookieName);
        if (cookie == null || !StringUtils.hasText(cookie.getValue())) {
            return null;
        }
        return cookie.getValue().trim();
    }

    public void addSessionCookie(ServerWebExchange exchange, String sessionId) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(sessionProperties.getSessionCookieName())) {
            return;
        }
        exchange.getResponse().addCookie(ResponseCookie.from(sessionProperties.getSessionCookieName(), sessionId)
                .path(StringUtils.hasText(sessionProperties.getSessionCookiePath())
                        ? sessionProperties.getSessionCookiePath()
                        : "/")
                .httpOnly(true)
                .secure(isSecure(exchange))
                .sameSite("Lax")
                .build());
    }

    public void expireSessionCookie(ServerWebExchange exchange) {
        if (!StringUtils.hasText(sessionProperties.getSessionCookieName())) {
            return;
        }
        exchange.getResponse().addCookie(ResponseCookie.from(sessionProperties.getSessionCookieName(), "")
                .path(StringUtils.hasText(sessionProperties.getSessionCookiePath())
                        ? sessionProperties.getSessionCookiePath()
                        : "/")
                .httpOnly(true)
                .secure(isSecure(exchange))
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build());
    }

    private boolean isSecure(ServerWebExchange exchange) {
        return "https".equalsIgnoreCase(exchange.getRequest().getURI().getScheme());
    }
}
