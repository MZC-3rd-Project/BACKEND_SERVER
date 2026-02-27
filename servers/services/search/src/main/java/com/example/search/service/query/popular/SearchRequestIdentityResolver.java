package com.example.search.service.query.popular;

import com.example.contracts.http.HttpHeaderNames;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class SearchRequestIdentityResolver {

    private static final String SESSION_HEADER = "X-Session-Id";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    public String resolveIdentity() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "anonymous";
        }
        HttpServletRequest request = attributes.getRequest();
        if (request == null) {
            return "anonymous";
        }

        String userId = request.getHeader(HttpHeaderNames.USER_ID);
        if (StringUtils.hasText(userId)) {
            return "user:" + userId.trim();
        }

        String sessionId = request.getHeader(SESSION_HEADER);
        if (!StringUtils.hasText(sessionId)) {
            sessionId = request.getRequestedSessionId();
        }
        if (StringUtils.hasText(sessionId)) {
            return "session:" + sessionId.trim();
        }

        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (StringUtils.hasText(forwardedFor)) {
            String firstIp = forwardedFor.split(",")[0].trim();
            if (StringUtils.hasText(firstIp)) {
                return "ip:" + firstIp;
            }
        }

        if (StringUtils.hasText(request.getRemoteAddr())) {
            return "ip:" + request.getRemoteAddr();
        }

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(requestId)) {
            return "request:" + requestId.trim();
        }
        return "anonymous";
    }
}
