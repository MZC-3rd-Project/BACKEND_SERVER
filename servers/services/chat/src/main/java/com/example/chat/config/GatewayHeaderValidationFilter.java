package com.example.chat.config;

import com.example.contracts.http.HttpHeaderNames;
import com.example.security.context.AuthContext;
import com.example.security.context.AuthContextHolder;
import com.example.security.context.HeaderSecurityException;
import com.example.security.context.SignedHeaderParser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class GatewayHeaderValidationFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/v1/chat";
    private static final String WS_PREFIX = "/ws/chat";

    private final ChatSecurityProperties properties;
    private final SignedHeaderParser signedHeaderParser;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (properties.isGatewayAuthEnabled() && !validateGatewayAuth(request)) {
            writeUnauthorized(response, "CHAT-AUTH-001", "gateway authentication failed");
            return;
        }

        if (!validateUserContext(request)) {
            writeUnauthorized(response, "CHAT-AUTH-002", "invalid user header");
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return true;
        }
        if (uri.startsWith("/actuator") || uri.startsWith("/v3/api-docs") || uri.startsWith("/swagger-ui")) {
            return true;
        }
        return !(uri.startsWith(API_PREFIX) || uri.startsWith(WS_PREFIX));
    }

    private boolean validateGatewayAuth(HttpServletRequest request) {
        String token = properties.getInternalAuthToken();
        if (!StringUtils.hasText(token)) {
            // Token 미설정 환경에서는 서명 헤더 파서를 통해 인증을 강제
            return signedHeaderParser != null;
        }
        String provided = request.getHeader(properties.getInternalAuthHeader());
        return token.equals(provided);
    }

    private boolean validateUserContext(HttpServletRequest request) {
        if (signedHeaderParser != null) {
            return validateSignedHeaders(request);
        }
        return validateLegacyHeaders(request);
    }

    private boolean validateSignedHeaders(HttpServletRequest request) {
        try {
            AuthContext authContext = signedHeaderParser.parse(request::getHeader);
            if (!isPositiveLong(authContext.getUserId())) {
                return false;
            }
            AuthContextHolder.setContext(authContext);
            return true;
        } catch (HeaderSecurityException e) {
            log.debug("Signed header validation failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean validateLegacyHeaders(HttpServletRequest request) {
        String userIdRaw = request.getHeader(properties.getUserIdHeader());
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

    private void writeUnauthorized(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {
                  "success": false,
                  "error": {
                    "code": "%s",
                    "message": "%s"
                  },
                  "timestamp": "%s"
                }
                """.formatted(code, message, Instant.now()));
    }
}
