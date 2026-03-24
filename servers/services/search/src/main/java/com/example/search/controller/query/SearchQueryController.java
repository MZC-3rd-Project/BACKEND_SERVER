package com.example.search.controller.query;

import com.example.api.response.ApiResponse;
import com.example.core.pagination.CursorResponse;
import com.example.search.dto.response.SearchItemResponse;
import com.example.search.dto.response.SearchSuggestionResponse;
import com.example.search.service.analytics.SearchAnalyticsEventService;
import com.example.search.service.analytics.SearchRequestContext;
import com.example.search.service.query.SearchQuery;
import com.example.search.service.query.SearchQueryService;
import com.example.search.service.query.SearchSuggestionService;
import com.example.security.gateway.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Search", description = "통합 검색 API")
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchQueryController {

    private static final String SESSION_ID_HEADER = "X-Session-Id";
    private static final String JOURNEY_ID_HEADER = "X-Journey-Id";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String CAUSATION_ID_HEADER = "X-Causation-Id";

    private final SearchQueryService searchQueryService;
    private final SearchSuggestionService searchSuggestionService;
    private final SearchAnalyticsEventService searchAnalyticsEventService;

    @Operation(summary = "통합 상품 검색")
    @GetMapping
    public ApiResponse<CursorResponse<SearchItemResponse>> search(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "domainType", required = false) String domainType,
            @RequestParam(name = "status", required = false) List<String> statuses,
            @RequestParam(name = "minPrice", required = false) Long minPrice,
            @RequestParam(name = "maxPrice", required = false) Long maxPrice,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "size", required = false) Integer size,
            @CurrentUserId(required = false) Long userId,
            @RequestHeader(name = SESSION_ID_HEADER, required = false) String sessionId,
            @RequestHeader(name = JOURNEY_ID_HEADER, required = false) String journeyId,
            @RequestHeader(name = CORRELATION_ID_HEADER, required = false) String correlationId,
            @RequestHeader(name = CAUSATION_ID_HEADER, required = false) String causationId
    ) {
        SearchQuery searchQuery = SearchQuery.of(
                query,
                category,
                domainType,
                statuses,
                minPrice,
                maxPrice,
                sort,
                cursor,
                size
        );
        CursorResponse<SearchItemResponse> response = searchQueryService.search(searchQuery);
        searchAnalyticsEventService.publishSearchExecuted(
                searchQuery,
                response,
                SearchRequestContext.of(userId, sessionId, journeyId, correlationId, causationId)
        );
        return ApiResponse.success(response);
    }

    @Operation(summary = "검색 자동완성 제안")
    @GetMapping("/suggestions")
    public ApiResponse<List<SearchSuggestionResponse>> suggestions(
            @RequestParam(name = "q") String query,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        return ApiResponse.success(searchSuggestionService.suggest(query, size));
    }
}
