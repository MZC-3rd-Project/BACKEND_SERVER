package com.example.search.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
public class GatewayHeaderValidationFilter extends OncePerRequestFilter {

    private static final String INTERNAL_API_PREFIX = "/internal/v1/search";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String USER_ID_HEADER = "X-User-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        logInternalAudit(request, resolveCaller(request));
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

    private String resolveCaller(HttpServletRequest request) {
        String userId = request.getHeader(USER_ID_HEADER);
        if (StringUtils.hasText(userId)) {
            return userId;
        }
        return "internal-client";
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
}
