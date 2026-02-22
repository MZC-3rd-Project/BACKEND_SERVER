package com.example.gateway.security;

import com.example.contracts.http.HttpHeaderNames;
import com.example.gateway.config.GatewaySecurityProperties;
import com.example.security.signature.HmacSigner;
import com.example.security.gateway.GatewayContextHeaderCodec;
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
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHeaderRelayGlobalFilter implements GlobalFilter, Ordered {

    private static final Set<String> WRITE_HTTP_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> REWRITE_BFF_TO_DOWNSTREAM_PATHS = Set.of("/bff/v1/items");

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

        String method = exchange.getRequest().getMethod() != null
                ? exchange.getRequest().getMethod().name()
                : null;
        boolean jwtRequired = isJwtRequiredPath(path, method);
        GatewayJwtPrincipal principal = null;
        if (StringUtils.hasText(bearerToken)) {
            try {
                principal = jwtClaimParser.parse(bearerToken);
            } catch (JwtClaimParseException e) {
                return unauthorized(exchange, "GW-AUTH-002", e.getMessage());
            }
        } else if (jwtRequired) {
            return unauthorized(exchange, "GW-AUTH-003", "요청 경로는 Bearer 토큰이 필요합니다");
        }

        SignedContextHeader signedContextHeader = null;
        try {
            if (principal != null) {
                signedContextHeader = createSignedContextHeader(principal);
            }
        } catch (IllegalArgumentException e) {
            return unauthorized(exchange, "GW-AUTH-004", "서명 헤더 생성에 실패했습니다");
        }

        GatewayJwtPrincipal finalPrincipal = principal;
        SignedContextHeader finalSignedContextHeader = signedContextHeader;
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    removeSensitiveHeaders(headers);
                    applyInternalAuthHeader(headers);
                    applyPrincipalHeaders(headers, finalPrincipal, finalSignedContextHeader);
                })
                .build();

        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private boolean isTargetPath(String path) {
        return matchesAnyPrefix(path, securityProperties.getRelayPathPrefixes());
    }

    private boolean isJwtRequiredPath(String path, String method) {
        String jwtMatchingPath = mapPathForJwtRequirement(path);
        if (matchesAnyPrefix(jwtMatchingPath, securityProperties.getRequireJwtPathPrefixes())) {
            return true;
        }
        return isWriteMethod(method) && matchesAnyPrefix(jwtMatchingPath, securityProperties.getRequireJwtWritePathPrefixes());
    }

    private boolean matchesAnyPrefix(String path, List<String> prefixes) {
        if (!StringUtils.hasText(path) || prefixes == null || prefixes.isEmpty()) {
            return false;
        }
        return prefixes.stream()
                .filter(StringUtils::hasText)
                .anyMatch(path::startsWith);
    }

    private boolean isWriteMethod(String method) {
        if (!StringUtils.hasText(method)) {
            return false;
        }
        return WRITE_HTTP_METHODS.contains(method.toUpperCase(Locale.ROOT));
    }

    private String mapPathForJwtRequirement(String path) {
        if (!StringUtils.hasText(path)) {
            return path;
        }
        if (REWRITE_BFF_TO_DOWNSTREAM_PATHS.contains(path)) {
            return "/api/items";
        }
        return path;
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

    private SignedContextHeader createSignedContextHeader(GatewayJwtPrincipal principal) {
        HmacSigner signer = hmacSignerProvider.getIfAvailable();
        if (signer == null) {
            return null;
        }

        String userId = String.valueOf(principal.userId());
        String roles = principal.rolesHeaderValue();
        String nonce = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();
        String gatewayContext = GatewayContextHeaderCodec.encodeSigned(userId, roles, nonce, timestamp, signer);
        return new SignedContextHeader(gatewayContext);
    }

    private void removeSensitiveHeaders(HttpHeaders headers) {
        headers.remove(HttpHeaderNames.USER_ID);
        headers.remove(HttpHeaderNames.USER_ROLES);
        headers.remove(HttpHeaderNames.NONCE);
        headers.remove(HttpHeaderNames.TIMESTAMP);
        headers.remove(HttpHeaderNames.SIGNATURE);
        headers.remove(HttpHeaderNames.GATEWAY_CONTEXT);
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
                                       SignedContextHeader signedContextHeader) {
        if (principal == null) {
            return;
        }

        headers.set(HttpHeaderNames.USER_ID, String.valueOf(principal.userId()));
        String rolesHeader = principal.rolesHeaderValue();
        headers.set(HttpHeaderNames.USER_ROLES, rolesHeader);

        if (signedContextHeader != null) {
            headers.set(HttpHeaderNames.GATEWAY_CONTEXT, signedContextHeader.gatewayContext());
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

    private record SignedContextHeader(String gatewayContext) {
    }
}
