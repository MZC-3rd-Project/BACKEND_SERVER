package com.example.gateway.security;

import com.example.contracts.http.HttpHeaderNames;
import com.example.gateway.config.BusinessGatewaySecurityProperties;
import com.example.gateway.config.BusinessGatewaySessionProperties;
import com.example.gateway.security.session.application.GatewaySessionPrincipalResolver;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionHeaderRelayGlobalFilter implements GlobalFilter, Ordered {

    private static final Set<String> WRITE_HTTP_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final GatewaySessionPrincipalResolver sessionPrincipalResolver;
    private final BusinessGatewaySecurityProperties securityProperties;
    private final BusinessGatewaySessionProperties sessionProperties;
    private final ObjectProvider<HmacSigner> hmacSignerProvider;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!isTargetPath(path)) {
            return chain.filter(exchange);
        }

        String method = exchange.getRequest().getMethod() != null
                ? exchange.getRequest().getMethod().name()
                : null;
        boolean authRequired = isAuthRequiredPath(path, method);

        Mono<Optional<GatewaySessionPrincipal>> principalMono = sessionPrincipalResolver.resolve(exchange)
                .map(Optional::of)
                .onErrorResume(SessionClaimParseException.class, error -> {
                    if (authRequired) {
                        return Mono.error(error);
                    }
                    log.info("Business gateway session principal parsing skipped. path={}, method={}, reason={}",
                            path, method, error.getMessage());
                    return Mono.just(Optional.empty());
                })
                .defaultIfEmpty(Optional.empty());

        return principalMono
                .flatMap(optionalPrincipal -> {
                    if (optionalPrincipal.isEmpty()) {
                        if (authRequired) {
                            return unauthorized(exchange, "BIZ-GW-AUTH-001", "요청 경로는 인증 정보가 필요합니다");
                        }
                        return relay(exchange, chain, null);
                    }
                    return relay(exchange, chain, optionalPrincipal.get());
                })
                .onErrorResume(SessionClaimParseException.class,
                        error -> unauthorized(exchange, "BIZ-GW-AUTH-002", error.getMessage()));
    }

    private Mono<Void> relay(ServerWebExchange exchange, GatewayFilterChain chain, GatewaySessionPrincipal principal) {
        SignedContextHeader signedContextHeader = null;
        try {
            if (principal != null) {
                signedContextHeader = createSignedContextHeader(principal);
            }
        } catch (IllegalArgumentException e) {
            return unauthorized(exchange, "BIZ-GW-AUTH-003", "서명 헤더 생성에 실패했습니다");
        }

        SignedContextHeader finalSignedContextHeader = signedContextHeader;
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    removeSensitiveHeaders(headers, principal);
                    applyInternalAuthHeader(headers);
                    applyPrincipalHeaders(headers, principal, finalSignedContextHeader);
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

    private boolean isAuthRequiredPath(String path, String method) {
        if (matchesAnyPrefix(path, securityProperties.getRequireAuthPathPrefixes())) {
            return true;
        }
        return isWriteMethod(method) && matchesAnyPrefix(path, securityProperties.getRequireAuthWritePathPrefixes());
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

    private SignedContextHeader createSignedContextHeader(GatewaySessionPrincipal principal) {
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

    private void removeSensitiveHeaders(HttpHeaders headers, GatewaySessionPrincipal principal) {
        boolean preserveClientIdentityHeaders = securityProperties.isAllowClientIdentityHeaders() && principal == null;
        boolean preserveClientSignedContextHeader =
                securityProperties.isAllowClientSignedContextHeader() && principal == null;
        if (!preserveClientIdentityHeaders) {
            headers.remove(HttpHeaderNames.USER_ID);
            headers.remove(HttpHeaderNames.USER_ROLES);
        }
        headers.remove(HttpHeaderNames.NONCE);
        headers.remove(HttpHeaderNames.TIMESTAMP);
        headers.remove(HttpHeaderNames.SIGNATURE);
        if (!preserveClientSignedContextHeader) {
            headers.remove(HttpHeaderNames.GATEWAY_CONTEXT);
        }
        headers.remove(HttpHeaderNames.GATEWAY_AUTH);
        headers.remove(HttpHeaderNames.SESSION_ID);
    }

    private void applyInternalAuthHeader(HttpHeaders headers) {
        if (StringUtils.hasText(securityProperties.getInternalAuthToken())
                && StringUtils.hasText(securityProperties.getInternalAuthHeader())) {
            headers.set(securityProperties.getInternalAuthHeader(), securityProperties.getInternalAuthToken());
        }
    }

    private void applyPrincipalHeaders(HttpHeaders headers,
                                       GatewaySessionPrincipal principal,
                                       SignedContextHeader signedContextHeader) {
        if (principal == null) {
            return;
        }
        headers.set(HttpHeaderNames.USER_ID, String.valueOf(principal.userId()));
        headers.set(HttpHeaderNames.USER_ROLES, principal.rolesHeaderValue());
        if (sessionProperties.isRelayHeaderEnabled() && StringUtils.hasText(principal.sessionId())) {
            headers.set(HttpHeaderNames.SESSION_ID, principal.sessionId());
        }
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
