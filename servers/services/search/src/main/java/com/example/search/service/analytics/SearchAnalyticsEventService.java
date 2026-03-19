package com.example.search.service.analytics;

import com.example.core.pagination.CursorResponse;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.search.client.ProductSearchSourceClient;
import com.example.search.client.dto.ProductSearchDocument;
import com.example.search.dto.request.SearchClickTrackRequest;
import com.example.search.dto.response.SearchItemResponse;
import com.example.search.event.SearchExecutedEvent;
import com.example.search.event.SearchItemClickedEvent;
import com.example.search.service.query.SearchQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchAnalyticsEventService {

    private static final String TRACE_ID_KEY = "traceId";
    private static final int MAX_RESULT_ITEM_IDS = 20;

    private final EventPublisher eventPublisher;
    private final ProductSearchSourceClient productSearchSourceClient;

    public void publishSearchExecuted(
            SearchQuery searchQuery,
            CursorResponse<SearchItemResponse> response,
            SearchRequestContext context
    ) {
        try {
            SearchRequestContext resolvedContext = withTraceCorrelation(context);
            if (!isTrackableUser(resolvedContext)) {
                return;
            }

            String queryHash = hashQuery(searchQuery == null ? null : searchQuery.query());
            List<Long> resultItemIds = response == null || response.getItems() == null
                    ? List.of()
                    : response.getItems().stream()
                            .map(SearchItemResponse::itemId)
                            .filter(Objects::nonNull)
                            .limit(MAX_RESULT_ITEM_IDS)
                            .toList();

            SearchExecutedEvent event = new SearchExecutedEvent(
                    queryHash,
                    resolvedContext.sessionId(),
                    resolvedContext.userId(),
                    resolvedContext.journeyId(),
                    resolvedContext.correlationId(),
                    resolvedContext.causationId(),
                    resultItemIds
            );

            eventPublisher.publish(
                    event,
                    EventMetadata.of(
                            "SearchQuery",
                            firstNonBlank(queryHash, firstNonBlank(resolvedContext.sessionId(), "ANONYMOUS")),
                            resolvedContext.correlationId(),
                            resolvedContext.causationId()
                    )
            );
        } catch (Exception exception) {
            log.warn("Search executed analytics event publish skipped", exception);
        }
    }

    public void publishItemClicked(SearchClickTrackRequest request, SearchRequestContext context) {
        try {
            SearchRequestContext resolvedContext = withTraceCorrelation(context).overlay(
                    request == null ? null : request.sessionId(),
                    request == null ? null : request.journeyId(),
                    request == null ? null : request.correlationId(),
                    request == null ? null : request.causationId()
            );
            if (!isTrackableUser(resolvedContext)) {
                return;
            }

            String queryHash = resolveQueryHash(request);
            Ownership ownership = resolveOwnership(request);
            SearchItemClickedEvent event = new SearchItemClickedEvent(
                    request.itemId(),
                    ownership.storeId(),
                    ownership.sellerId(),
                    queryHash,
                    resolvedContext.sessionId(),
                    resolvedContext.userId(),
                    resolvedContext.journeyId(),
                    resolvedContext.correlationId(),
                    resolvedContext.causationId()
            );

            eventPublisher.publish(
                    event,
                    EventMetadata.of(
                            "SearchItem",
                            String.valueOf(request.itemId()),
                            resolvedContext.correlationId(),
                            resolvedContext.causationId()
                    )
            );
        } catch (Exception exception) {
            Long itemId = request == null ? null : request.itemId();
            log.warn("Search item clicked analytics event publish skipped. itemId={}", itemId, exception);
        }
    }

    private Ownership resolveOwnership(SearchClickTrackRequest request) {
        if (request == null) {
            return new Ownership(null, null);
        }
        if (request.storeId() != null && request.sellerId() != null) {
            return new Ownership(request.storeId(), request.sellerId());
        }

        Optional<ProductSearchDocument> document = productSearchSourceClient.findSearchDocument(request.itemId());
        return document
                .map(found -> new Ownership(
                        firstNonNull(request.storeId(), found.storeId()),
                        firstNonNull(request.sellerId(), found.sellerId())
                ))
                .orElseGet(() -> new Ownership(request.storeId(), request.sellerId()));
    }

    private SearchRequestContext withTraceCorrelation(SearchRequestContext context) {
        SearchRequestContext safeContext = context == null
                ? SearchRequestContext.of(null, null, null, null, null)
                : context;
        return safeContext.overlay(null, null, MDC.get(TRACE_ID_KEY), null);
    }

    private boolean isTrackableUser(SearchRequestContext context) {
        return context != null && context.userId() != null;
    }

    private String resolveQueryHash(SearchClickTrackRequest request) {
        if (request == null) {
            return null;
        }
        if (StringUtils.hasText(request.queryHash())) {
            return request.queryHash().trim();
        }
        return hashQuery(request.query());
    }

    static String hashQuery(String rawQuery) {
        if (!StringUtils.hasText(rawQuery)) {
            return null;
        }

        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(rawQuery.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("query hash generation failed", exception);
        }
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (StringUtils.hasText(primary)) {
            return primary.trim();
        }
        return StringUtils.hasText(fallback) ? fallback.trim() : null;
    }

    private static Long firstNonNull(Long primary, Long fallback) {
        return primary != null ? primary : fallback;
    }

    private record Ownership(Long storeId, Long sellerId) {
    }
}
