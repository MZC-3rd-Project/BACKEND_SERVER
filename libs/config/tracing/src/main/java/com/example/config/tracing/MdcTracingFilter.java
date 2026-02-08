package com.example.config.tracing;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@ConditionalOnClass(name = "jakarta.servlet.Filter")
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class MdcTracingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String SPAN_ID_KEY = "spanId";

    private final Tracer tracer;

    public MdcTracingFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            if (tracer != null && tracer.currentSpan() != null) {
                var context = tracer.currentSpan().context();
                if (context != null) {
                    MDC.put(TRACE_ID_KEY, context.traceId());
                    MDC.put(SPAN_ID_KEY, context.spanId());
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
            MDC.remove(SPAN_ID_KEY);
        }
    }
}
