package com.example.security.starter.servlet.filter;

import com.example.security.starter.servlet.client.GatewaySecurityClient;
import com.example.security.starter.servlet.properties.GatewaySecurityModuleProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
public class GatewaySecurityValidationFilter extends OncePerRequestFilter {

    private static final String GATEWAY_AUTH_ERROR_MESSAGE = "gateway authentication failed";
    private static final String USER_CONTEXT_ERROR_MESSAGE = "invalid user header";

    private final GatewaySecurityClient securityClient;
    private final GatewaySecurityModuleProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!securityClient.validateGatewayAuth(request)) {
            writeUnauthorized(response, properties.getGatewayAuthErrorCode(), GATEWAY_AUTH_ERROR_MESSAGE);
            return;
        }

        String uri = request.getRequestURI();
        boolean requiredPath = matches(uri, properties.getRequiredPaths());
        boolean validUserContext = requiredPath
                ? securityClient.validateUserContext(request)
                : securityClient.validateOptionalUserContext(request);
        if (!validUserContext) {
            writeUnauthorized(response, properties.getUserContextErrorCode(), USER_CONTEXT_ERROR_MESSAGE);
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!StringUtils.hasText(uri)) {
            return true;
        }
        if (matches(uri, properties.getExcludedPaths())) {
            return true;
        }
        return !matches(uri, properties.getRequiredPaths()) && !matches(uri, properties.getOptionalUserContextPaths());
    }

    private boolean matches(String uri, List<String> patterns) {
        if (!StringUtils.hasText(uri) || patterns == null || patterns.isEmpty()) {
            return false;
        }
        return patterns.stream()
                .filter(StringUtils::hasText)
                .anyMatch(pattern -> pathMatcher.match(pattern, uri));
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
