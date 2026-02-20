package com.example.search.config;

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
import java.util.Locale;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class GatewayHeaderValidationFilter extends OncePerRequestFilter {

    private static final String INTERNAL_API_PREFIX = "/internal/v1/search";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final Set<String> INTERNAL_ROLES = Set.of(
            "ROLE_INTERNAL", "INTERNAL", "ROLE_ADMIN", "ADMIN", "SYSTEM", "ROLE_SYSTEM"
    );

    private final SearchSecurityProperties properties;
    private final SignedHeaderParser signedHeaderParser;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ValidationResult validation = validateGatewayAuth(request);
        if (properties.isGatewayAuthEnabled() && !validation.authorized()) {
            writeUnauthorized(response, "SEARCH-AUTH-001", "gateway authentication failed");
            return;
        }

        logInternalAudit(request, validation.caller());
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
        return !uri.startsWith(INTERNAL_API_PREFIX);
    }

    private ValidationResult validateGatewayAuth(HttpServletRequest request) {
        String token = properties.getInternalAuthToken();
        if (!StringUtils.hasText(token)) {
            if (signedHeaderParser == null) {
                return new ValidationResult(false, "unknown");
            }
            try {
                var authContext = signedHeaderParser.parse(request::getHeader);
                boolean hasInternalRole = authContext.getRoles().stream()
                        .filter(StringUtils::hasText)
                        .map(role -> role.trim().toUpperCase(Locale.ROOT))
                        .anyMatch(INTERNAL_ROLES::contains);
                return new ValidationResult(hasInternalRole, authContext.getUserId());
            } catch (Exception e) {
                log.debug("Signed header verification failed for internal API.", e);
                return new ValidationResult(false, "unknown");
            }
        }
        String provided = request.getHeader(properties.getInternalAuthHeader());
        boolean authorized = token.equals(provided);
        String caller = StringUtils.hasText(request.getHeader("X-User-Id"))
                ? request.getHeader("X-User-Id")
                : "gateway-token";
        return new ValidationResult(authorized, caller);
    }

    private void logInternalAudit(HttpServletRequest request, String caller) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        String safeRequestId = StringUtils.hasText(requestId) ? requestId : "missing";
        String safeCaller = StringUtils.hasText(caller) ? caller : "unknown";
        log.info("[SearchInternalAudit] method={}, uri={}, caller={}, requestId={}, remoteIp={}",
                request.getMethod(),
                request.getRequestURI(),
                safeCaller,
                safeRequestId,
                request.getRemoteAddr());
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

    private record ValidationResult(boolean authorized, String caller) {
    }
}
