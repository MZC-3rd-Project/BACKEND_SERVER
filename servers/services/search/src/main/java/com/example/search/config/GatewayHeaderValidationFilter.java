package com.example.search.config;

import com.example.security.context.SignedHeaderParser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@RequiredArgsConstructor
public class GatewayHeaderValidationFilter extends OncePerRequestFilter {

    private static final String INTERNAL_API_PREFIX = "/internal/v1/search";

    private final SearchSecurityProperties properties;
    private final SignedHeaderParser signedHeaderParser;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (properties.isGatewayAuthEnabled() && !validateGatewayAuth(request)) {
            writeUnauthorized(response, "SEARCH-AUTH-001", "gateway authentication failed");
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
        return !uri.startsWith(INTERNAL_API_PREFIX);
    }

    private boolean validateGatewayAuth(HttpServletRequest request) {
        String token = properties.getInternalAuthToken();
        if (!StringUtils.hasText(token)) {
            return signedHeaderParser != null;
        }
        String provided = request.getHeader(properties.getInternalAuthHeader());
        return token.equals(provided);
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
