package com.example.search.config;

import com.example.contracts.http.HttpHeaderNames;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
public class SearchRequestAuditFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/v1/search";
    private static final String INTERNAL_API_PREFIX = "/internal/v1/search";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String INTERNAL_CALLER = "internal-client";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        logRequestAudit(request, resolveCaller(request));
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!StringUtils.hasText(uri)) {
            return true;
        }
        if (uri.startsWith("/actuator") || uri.startsWith("/v3/api-docs") || uri.startsWith("/swagger-ui")) {
            return true;
        }
        return !(uri.startsWith(API_PREFIX) || uri.startsWith(INTERNAL_API_PREFIX));
    }

    private String resolveCaller(HttpServletRequest request) {
        String userId = request.getHeader(HttpHeaderNames.USER_ID);
        if (StringUtils.hasText(userId)) {
            return userId;
        }
        return INTERNAL_CALLER;
    }

    private void logRequestAudit(HttpServletRequest request, String caller) {
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
