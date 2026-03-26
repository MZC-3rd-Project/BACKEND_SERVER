package com.example.config.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class ServletRequestIdMdcFilter extends OncePerRequestFilter {

    private final LoggingProperties properties;

    public ServletRequestIdMdcFilter(LoggingProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = RequestIdSupport.resolveRequestId(request.getHeader(properties.getRequestIdHeader()));
        request.setAttribute(RequestIdSupport.REQUEST_ID_KEY, requestId);

        if (properties.isWriteRequestIdResponseHeader()) {
            response.setHeader(properties.getRequestIdHeader(), requestId);
        }

        try {
            RequestIdSupport.putRequestId(requestId);
            filterChain.doFilter(request, response);
        } finally {
            RequestIdSupport.clearRequestId();
        }
    }
}
