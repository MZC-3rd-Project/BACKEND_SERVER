package com.example.gateway.security;

import com.example.contracts.http.HttpHeaderNames;
import com.example.security.gateway.GatewayContextHeaderCodec;
import com.example.security.signature.HmacSigner;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gateway.session", name = {"enabled", "trusted-header-auth-enabled"}, havingValue = "true")
public class TrustedHeaderSessionAuthenticationWebFilter implements WebFilter {

    private static final String CODE_INVALID_REQUEST = "GW-AUTH-009";

    private final SessionClaimParser sessionClaimParser;
    private final ObjectProvider<HmacSigner> hmacSignerProvider;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String gatewayContextHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaderNames.GATEWAY_CONTEXT);
        String sessionIdHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaderNames.SESSION_ID);

        if (!StringUtils.hasText(gatewayContextHeader) && !StringUtils.hasText(sessionIdHeader)) {
            return chain.filter(exchange);
        }

        if (!StringUtils.hasText(gatewayContextHeader) || !StringUtils.hasText(sessionIdHeader)) {
            return unauthorized(exchange, "gateway context와 session id는 함께 전달되어야 합니다");
        }

        Authentication authentication;
        try {
            authentication = createAuthentication(gatewayContextHeader, sessionIdHeader);
        } catch (SessionClaimParseException e) {
            return unauthorized(exchange, e.getMessage());
        }

        ServerWebExchange authenticatedExchange = exchange.mutate()
                .principal(Mono.just(authentication))
                .build();

        return chain.filter(authenticatedExchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    private Authentication createAuthentication(String gatewayContextHeader, String sessionIdHeader) {
        HmacSigner signer = hmacSignerProvider.getIfAvailable();
        if (signer == null) {
            throw new SessionClaimParseException("서명 검증기가 설정되지 않았습니다");
        }

        GatewayContextHeaderCodec.ParsedGatewayContext parsed;
        try {
            parsed = GatewayContextHeaderCodec.decode(gatewayContextHeader);
        } catch (RuntimeException e) {
            throw new SessionClaimParseException("유효하지 않은 gateway context 헤더입니다", e);
        }
        String payload = HmacSigner.buildSignaturePayload(
                parsed.userId(),
                parsed.roles(),
                parsed.nonce(),
                parsed.timestamp()
        );
        if (!signer.verify(payload, parsed.signature())) {
            throw new SessionClaimParseException("gateway context 서명 검증에 실패했습니다");
        }

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", parsed.userId());
        List<String> roles = parseRoles(parsed.roles());
        if (!roles.isEmpty()) {
            claims.put("roles", roles);
        }
        claims.put("sid", sessionIdHeader.trim());

        GatewaySessionPrincipal principal = sessionClaimParser.parseClaims(claims);
        List<GrantedAuthority> authorities = principal.roles().stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role))
                .toList();

        DefaultOAuth2AuthenticatedPrincipal oauthPrincipal =
                new DefaultOAuth2AuthenticatedPrincipal(claims, authorities);
        return new UsernamePasswordAuthenticationToken(oauthPrincipal, "N/A", authorities);
    }

    private List<String> parseRoles(String rawRoles) {
        if (!StringUtils.hasText(rawRoles)) {
            return List.of();
        }
        return Arrays.stream(rawRoles.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {
                  "success": false,
                  "error": {
                    "code": "%s",
                    "message": "%s"
                  }
                }
                """.formatted(CODE_INVALID_REQUEST, message);
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }
}
