package com.example.gateway.security.session.application;

import com.example.gateway.config.GatewaySessionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class GatewaySessionCookieManager {

    private final GatewaySessionProperties sessionProperties;

    public String extractSessionId(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(sessionProperties.getSessionCookieName());
        if (cookie == null || !StringUtils.hasText(cookie.getValue())) {
            return null;
        }
        return cookie.getValue();
    }

    public void addSessionCookie(ServerWebExchange exchange, String sessionId) {
        log.info("Gateway session cookie set. path={}, sid={}",
                exchange.getRequest().getURI().getPath(),
                sessionId);
        exchange.getResponse().addCookie(ResponseCookie.from(sessionProperties.getSessionCookieName(), sessionId)
                .path(sessionProperties.getSessionCookiePath())
                .httpOnly(true)
                .secure("https".equalsIgnoreCase(exchange.getRequest().getURI().getScheme()))
                .sameSite("Lax")
                .build());
    }

    public void expireSessionCookie(ServerWebExchange exchange) {
        log.warn("Gateway session cookie expired. path={}, currentSid={}",
                exchange.getRequest().getURI().getPath(),
                extractSessionId(exchange));
        exchange.getResponse().addCookie(ResponseCookie.from(sessionProperties.getSessionCookieName(), "")
                .path(sessionProperties.getSessionCookiePath())
                .httpOnly(true)
                .secure("https".equalsIgnoreCase(exchange.getRequest().getURI().getScheme()))
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build());
    }
}
