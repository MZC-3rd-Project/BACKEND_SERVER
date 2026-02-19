package com.example.notification.config;

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

@Slf4j
@RequiredArgsConstructor
public class GatewayHeaderValidationFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/v1/notifications";

    private final NotificationSecurityProperties properties;
    private final SignedHeaderParser signedHeaderParser;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (properties.isGatewayAuthEnabled()) {
            if (!validateGatewayAuth(request)) {
                writeUnauthorized(response, "NOTIFICATION-AUTH-001", "gateway authentication failed");
                return;
            }
        }

        if (!validateUserContext(request)) {
            writeUnauthorized(response, "NOTIFICATION-AUTH-002", "invalid user header");
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
        return !uri.startsWith(API_PREFIX);
    }

    private boolean validateGatewayAuth(HttpServletRequest request) {
        String token = properties.getInternalAuthToken();
        if (!StringUtils.hasText(token)) {
            return signedHeaderParser != null;
        }
        String provided = request.getHeader(properties.getInternalAuthHeader());
        return token.equals(provided);
    }

    private boolean validateUserContext(HttpServletRequest request) {
        if (signedHeaderParser != null) {
            return validateSignedHeaders(request);
        }

        return validateLegacyUserHeader(request);
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

    private boolean validateLegacyUserHeader(HttpServletRequest request) {
        String userIdRaw = request.getHeader(properties.getUserIdHeader());
        return isPositiveLong(userIdRaw);
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
