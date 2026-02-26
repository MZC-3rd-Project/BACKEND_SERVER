package com.example.security.gateway;

import com.example.contracts.http.HttpHeaderNames;
import com.example.security.context.AuthContext;
import com.example.security.context.AuthContextHolder;
import com.example.security.signature.HeaderSecurityException;
import com.example.security.signature.SignedHeaderParser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class GatewayRequestVerifier implements GatewaySecurityClient {

    private final GatewayHeaderValidationProperties properties;
    private final SignedHeaderParser signedHeaderParser;

    public boolean validateGatewayAuth(HttpServletRequest request) {
        if (!properties.isGatewayAuthEnabled()) {
            return true;
        }

        String token = properties.getInternalAuthToken();
        if (!StringUtils.hasText(token)) {
            // 내부 토큰이 비어 있으면 서명 헤더 기반 검증 경로를 강제한다.
            return signedHeaderParser != null;
        }

        String provided = request.getHeader(properties.getInternalAuthHeader());
        return token.equals(provided);
    }

    public boolean validateUserContext(HttpServletRequest request) {
        return validateUserContext(request, true);
    }

    public boolean validateOptionalUserContext(HttpServletRequest request) {
        return validateUserContext(request, false);
    }

    private boolean validateUserContext(HttpServletRequest request, boolean required) {
        if (signedHeaderParser != null) {
            return validateGatewayContextHeader(request, required);
        }
        return validateLegacyHeaders(request, required);
    }

    private boolean validateGatewayContextHeader(HttpServletRequest request, boolean required) {
        String gatewayContextToken = request.getHeader(HttpHeaderNames.GATEWAY_CONTEXT);
        if (!StringUtils.hasText(gatewayContextToken)) {
            return !required;
        }
        return validateGatewayContextToken(gatewayContextToken);
    }

    private boolean validateGatewayContextToken(String token) {
        try {
            GatewayContextHeaderCodec.ParsedGatewayContext parsed = GatewayContextHeaderCodec.decode(token);
            AuthContext authContext = signedHeaderParser.parse(headerName -> switch (headerName) {
                case HttpHeaderNames.USER_ID -> parsed.userId();
                case HttpHeaderNames.USER_ROLES -> parsed.roles();
                case HttpHeaderNames.NONCE -> parsed.nonce();
                case HttpHeaderNames.TIMESTAMP -> String.valueOf(parsed.timestamp());
                case HttpHeaderNames.SIGNATURE -> parsed.signature();
                default -> null;
            });
            if (!isPositiveLong(authContext.getUserId())) {
                return false;
            }
            AuthContextHolder.setContext(authContext);
            return true;
        } catch (HeaderSecurityException e) {
            log.debug("Gateway context header validation failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean validateLegacyHeaders(HttpServletRequest request, boolean required) {
        String userIdRaw = request.getHeader(properties.getUserIdHeader());
        if (!StringUtils.hasText(userIdRaw)) {
            return !required;
        }
        if (!isPositiveLong(userIdRaw)) {
            return false;
        }

        List<String> roles = parseRoles(request.getHeader(HttpHeaderNames.USER_ROLES));
        AuthContextHolder.setContext(AuthContext.builder()
                .userId(userIdRaw)
                .roles(roles)
                .nonce(null)
                .timestamp(0)
                .build());
        return true;
    }

    private List<String> parseRoles(String rolesHeader) {
        if (!StringUtils.hasText(rolesHeader)) {
            return List.of();
        }
        return Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    private boolean isPositiveLong(String raw) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        try {
            return Long.parseLong(raw) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
