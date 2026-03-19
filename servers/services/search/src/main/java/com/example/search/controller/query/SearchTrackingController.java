package com.example.search.controller.query;

import com.example.api.response.ApiResponse;
import com.example.search.dto.request.SearchClickTrackRequest;
import com.example.search.service.analytics.SearchAnalyticsEventService;
import com.example.search.service.analytics.SearchRequestContext;
import com.example.security.gateway.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Search", description = "통합 검색 추적 API")
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchTrackingController {

    private static final String SESSION_ID_HEADER = "X-Session-Id";
    private static final String JOURNEY_ID_HEADER = "X-Journey-Id";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String CAUSATION_ID_HEADER = "X-Causation-Id";

    private final SearchAnalyticsEventService searchAnalyticsEventService;

    @Operation(summary = "검색 결과 클릭 추적")
    @PostMapping("/clicks")
    public ApiResponse<Void> trackClick(
            @Valid @RequestBody SearchClickTrackRequest request,
            @CurrentUserId(required = false) Long userId,
            @RequestHeader(name = SESSION_ID_HEADER, required = false) String sessionId,
            @RequestHeader(name = JOURNEY_ID_HEADER, required = false) String journeyId,
            @RequestHeader(name = CORRELATION_ID_HEADER, required = false) String correlationId,
            @RequestHeader(name = CAUSATION_ID_HEADER, required = false) String causationId
    ) {
        searchAnalyticsEventService.publishItemClicked(
                request,
                SearchRequestContext.of(userId, sessionId, journeyId, correlationId, causationId)
        );
        return ApiResponse.success();
    }
}
