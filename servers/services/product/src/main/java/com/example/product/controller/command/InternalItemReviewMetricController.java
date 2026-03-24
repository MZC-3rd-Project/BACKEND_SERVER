package com.example.product.controller.command;

import com.example.api.response.ApiResponse;
import com.example.product.dto.item.request.ReviewMetricsUpdateRequest;
import com.example.product.service.command.ItemReviewMetricCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Internal Item Review Metrics", description = "상품 리뷰 집계 내부 API")
@RestController
@RequestMapping("/internal/v1/items")
@RequiredArgsConstructor
public class InternalItemReviewMetricController {

    private final ItemReviewMetricCommandService itemReviewMetricCommandService;

    @Operation(summary = "상품 리뷰 집계 갱신 (내부)")
    @PutMapping("/{itemId}/review-metrics")
    public ApiResponse<Void> updateReviewMetrics(
            @PathVariable Long itemId,
            @Valid @RequestBody ReviewMetricsUpdateRequest request
    ) {
        itemReviewMetricCommandService.updateReviewMetrics(itemId, request);
        return ApiResponse.success(null);
    }
}
