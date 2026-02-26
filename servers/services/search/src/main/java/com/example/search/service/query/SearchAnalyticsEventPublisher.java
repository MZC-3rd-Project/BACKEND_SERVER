package com.example.search.service.query;

import com.example.contracts.http.HttpHeaderNames;
import com.example.core.pagination.CursorResponse;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.search.dto.search.request.SearchRequest;
import com.example.search.dto.search.response.SearchItemResponse;
import com.example.search.event.SearchExecutedEvent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchAnalyticsEventPublisher {

    private final ObjectProvider<EventPublisher> eventPublisherProvider;

    public void publishSearchExecuted(SearchRequest request, CursorResponse<SearchItemResponse> result) {
        EventPublisher eventPublisher = eventPublisherProvider.getIfAvailable();
        if (eventPublisher == null) {
            return;
        }

        HttpServletRequest httpRequest = currentHttpRequest();
        String sessionId = resolveSessionId(httpRequest);
        Long sellerId = parseLongHeader(httpRequest, HttpHeaderNames.USER_ID);
        Long storeId = parseLongHeader(httpRequest, HttpHeaderNames.STORE_ID);
        Long itemId = resolveFirstItemId(result);

        SearchExecutedEvent event = new SearchExecutedEvent(
                request.getQ(),
                sessionId,
                itemId,
                storeId,
                sellerId
        );

        String aggregateId = itemId != null ? String.valueOf(itemId) : event.getEventId();
        try {
            eventPublisher.publish(event, EventMetadata.of("SearchQuery", aggregateId));
        } catch (Exception e) {
            log.warn("Search analytics event publish failed. aggregateId={}", aggregateId, e);
        }
    }

    private Long resolveFirstItemId(CursorResponse<SearchItemResponse> result) {
        if (result == null || result.getItems() == null || result.getItems().isEmpty()) {
            return null;
        }
        return result.getItems().get(0).getItemId();
    }

    private HttpServletRequest currentHttpRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return attributes.getRequest();
    }

    private String resolveSessionId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String sessionId = request.getHeader(HttpHeaderNames.SESSION_ID);
        if (!StringUtils.hasText(sessionId)) {
            sessionId = request.getRequestedSessionId();
        }
        return StringUtils.hasText(sessionId) ? sessionId.trim() : null;
    }

    private Long parseLongHeader(HttpServletRequest request, String headerName) {
        if (request == null) {
            return null;
        }
        String value = request.getHeader(headerName);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
