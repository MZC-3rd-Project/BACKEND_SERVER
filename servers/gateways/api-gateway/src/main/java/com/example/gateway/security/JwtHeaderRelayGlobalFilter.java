package com.example.gateway.security;

import com.example.contracts.http.HttpHeaderNames;
import com.example.gateway.config.GatewaySecurityProperties;
import com.example.security.context.HmacSigner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHeaderRelayGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> TARGET_PATH_PREFIXES = List.of(
            "/api/v1/search",
            "/api/v1/chat",
            "/ws/chat"
    );

    private final JwtClaimParser jwtClaimParser;
    private final GatewaySecurityProperties securityProperties;
    private final ObjectProvider<HmacSigner> hmacSignerProvider;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!isTargetPath(path)) {
            return chain.filter(exchange);
        }

        String bearerToken;
        try {
            bearerToken = resolveBearerToken(exchange.getRequest().getHeaders());
        } catch (JwtClaimParseException e) {
            return unauthorized(exchange, "GW-AUTH-001", e.getMessage());
        }

        boolean jwtRequired = isJwtRequiredPath(path);
        GatewayJwtPrincipal principal = null;
        if (StringUtils.hasText(bearerToken)) {
            try {
                principal = jwtClaimParser.parse(bearerToken);
            } catch (JwtClaimParseException e) {
                return unauthorized(exchange, "GW-AUTH-002", e.getMessage());
            }
        } else if (jwtRequired) {
            return unauthorized(exchange, "GW-AUTH-003", "chat 경로는 Bearer 토큰이 필요합니다");
        }

        SignedHeaderValues signedHeaders = null;
        try {
            if (principal != null) {
                signedHeaders = createSignedHeaders(principal);
            }
        } catch (IllegalArgumentException e) {
            return unauthorized(exchange, "GW-AUTH-004", "서명 헤더 생성에 실패했습니다");
        }

        GatewayJwtPrincipal finalPrincipal = principal;
        SignedHeaderValues finalSignedHeaders = signedHeaders;
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    removeSensitiveHeaders(headers);
                    applyInternalAuthHeader(headers);
                    applyPrincipalHeaders(headers, finalPrincipal, finalSignedHeaders);
                })
                .build();

        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private boolean isTargetPath(String path) {
        return TARGET_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean isJwtRequiredPath(String path) {
        List<String> requiredPrefixes = securityProperties.getRequireJwtPathPrefixes();
        if (requiredPrefixes == null || requiredPrefixes.isEmpty()) {
            return false;
        }
        return requiredPrefixes.stream()
                .anyMatch(path::startsWith);
    }

    private String resolveBearerToken(HttpHeaders headers) {
        String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader)) {
            return null;
        }

        String prefix = "bearer ";
        if (!authHeader.toLowerCase(Locale.ROOT).startsWith(prefix)) {
            throw new JwtClaimParseException("Authorization 헤더는 Bearer 형식이어야 합니다");
        }

        String token = authHeader.substring(prefix.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new JwtClaimParseException("Bearer 토큰이 비어 있습니다");
        }
        return token;
    }

    private SignedHeaderValues createSignedHeaders(GatewayJwtPrincipal principal) {
        HmacSigner signer = hmacSignerProvider.getIfAvailable();
        if (signer == null) {
            return null;
        }

        String userId = String.valueOf(principal.userId());
        String roles = principal.rolesHeaderValue();
        String nonce = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();
        String payload = HmacSigner.buildSignaturePayload(userId, roles, nonce, timestamp);
        String signature = signer.sign(payload);
        return new SignedHeaderValues(nonce, timestamp, signature);
    }

    private void removeSensitiveHeaders(HttpHeaders headers) {
        headers.remove(HttpHeaderNames.USER_ID);
        headers.remove(HttpHeaderNames.USER_ROLES);
        headers.remove(HttpHeaderNames.NONCE);
        headers.remove(HttpHeaderNames.TIMESTAMP);
        headers.remove(HttpHeaderNames.SIGNATURE);
        headers.remove(HttpHeaderNames.GATEWAY_AUTH);
    }

    private void applyInternalAuthHeader(HttpHeaders headers) {
        if (StringUtils.hasText(securityProperties.getInternalAuthToken())
                && StringUtils.hasText(securityProperties.getInternalAuthHeader())) {
            headers.set(securityProperties.getInternalAuthHeader(), securityProperties.getInternalAuthToken());
        }
    }

    private void applyPrincipalHeaders(HttpHeaders headers,
                                       GatewayJwtPrincipal principal,
                                       SignedHeaderValues signedHeaders) {
        if (principal == null) {
            return;
        }

        headers.set(HttpHeaderNames.USER_ID, String.valueOf(principal.userId()));
        String rolesHeader = principal.rolesHeaderValue();
        headers.set(HttpHeaderNames.USER_ROLES, rolesHeader);

        if (signedHeaders != null) {
            headers.set(HttpHeaderNames.NONCE, signedHeaders.nonce());
            headers.set(HttpHeaderNames.TIMESTAMP, String.valueOf(signedHeaders.timestamp()));
            headers.set(HttpHeaderNames.SIGNATURE, signedHeaders.signature());
        }
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
        return response.writeWith(Mono.just(response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    private record SignedHeaderValues(String nonce, long timestamp, String signature) {
    }
}
