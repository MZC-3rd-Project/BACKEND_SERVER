package com.example.gateway.security;

import com.example.contracts.http.HttpHeaderNames;
import com.example.gateway.config.GatewaySecurityProperties;
import com.example.gateway.security.session.application.GatewaySessionPrincipalResolver;
import com.example.security.gateway.GatewayContextHeaderCodec;
import com.example.security.signature.HmacSigner;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SessionHeaderRelayGlobalFilter implements GlobalFilter, Ordered {

    private static final Set<String> WRITE_HTTP_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final GatewaySessionPrincipalResolver sessionPrincipalResolver;
    private final GatewaySecurityProperties securityProperties;
    private final ObjectProvider<HmacSigner> hmacSignerProvider;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!matchesAnyPrefix(path, securityProperties.getRelayPathPrefixes())) {
            return chain.filter(exchange);
        }
        boolean authRequired = isAuthRequired(path, exchange.getRequest().getMethod() != null
                ? exchange.getRequest().getMethod().name()
                : null);

        return sessionPrincipalResolver.resolve(exchange)
                .flatMap(principal -> chain.filter(exchange.mutate().request(
                        exchange.getRequest().mutate()
                                .headers(headers -> applyHeaders(headers, principal))
                                .build()
                ).build()))
                .switchIfEmpty(authRequired
                        ? unauthorized(exchange, "GW-AUTH-003", "요청 경로는 인증 정보가 필요합니다")
                        : chain.filter(exchange.mutate().request(
                                exchange.getRequest().mutate()
                                        .headers(this::applyAnonymousHeaders)
                                        .build()
                        ).build()));
    }

    private void applyHeaders(HttpHeaders headers, GatewaySessionPrincipal principal) {
        applyAnonymousHeaders(headers);
        headers.set(HttpHeaderNames.USER_ID, String.valueOf(principal.userId()));
        headers.set(HttpHeaderNames.USER_ROLES, principal.rolesHeaderValue());
        if (StringUtils.hasText(principal.sessionId())) {
            headers.set(HttpHeaderNames.SESSION_ID, principal.sessionId());
        }
        HmacSigner signer = hmacSignerProvider.getIfAvailable();
        if (signer != null) {
            String nonce = UUID.randomUUID().toString();
            long timestamp = System.currentTimeMillis();
            headers.set(HttpHeaderNames.GATEWAY_CONTEXT,
                    GatewayContextHeaderCodec.encodeSigned(
                            String.valueOf(principal.userId()),
                            principal.rolesHeaderValue(),
                            nonce,
                            timestamp,
                            signer
                    ));
        }
    }

    private void applyAnonymousHeaders(HttpHeaders headers) {
        headers.remove(HttpHeaderNames.USER_ID);
        headers.remove(HttpHeaderNames.USER_ROLES);
        headers.remove(HttpHeaderNames.NONCE);
        headers.remove(HttpHeaderNames.TIMESTAMP);
        headers.remove(HttpHeaderNames.SIGNATURE);
        headers.remove(HttpHeaderNames.GATEWAY_CONTEXT);
        headers.remove(HttpHeaderNames.SESSION_ID);
        if (StringUtils.hasText(securityProperties.getInternalAuthHeader())
                && StringUtils.hasText(securityProperties.getInternalAuthToken())) {
            headers.set(securityProperties.getInternalAuthHeader(), securityProperties.getInternalAuthToken());
        }
    }

    private boolean isAuthRequired(String path, String method) {
        if (matchesAnyPrefix(path, securityProperties.getRequireAuthPathPrefixes())) {
            return true;
        }
        return method != null
                && WRITE_HTTP_METHODS.contains(method.toUpperCase(Locale.ROOT))
                && matchesAnyPrefix(path, securityProperties.getRequireAuthWritePathPrefixes());
    }

    private boolean matchesAnyPrefix(String path, List<String> prefixes) {
        if (!StringUtils.hasText(path) || prefixes == null || prefixes.isEmpty()) {
            return false;
        }
        return prefixes.stream()
                .filter(StringUtils::hasText)
                .anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {
                  "success": false,
                  "error": {
                    "code": "%s",
                    "message": "%s"
                  }
                }
                """.formatted(code, message);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
