package com.example.order.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.order.dto.response.InternalOrderDetailResponse;
import com.example.order.dto.response.InternalOrderReviewEligibilityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Order Query (Internal)", description = "주문 조회 내부 API")
public interface InternalOrderQueryApi {

    @Operation(summary = "주문 단건 조회 (내부)", description = "orderId로 주문을 조회합니다")
    @GetMapping("/internal/v1/orders/{orderId}")
    ApiResponse<InternalOrderDetailResponse> getOrder(@PathVariable Long orderId);

    @Operation(summary = "리뷰 작성 가능 여부 조회 (내부)", description = "주문/사용자/상품 기준 리뷰 작성 가능 여부를 조회합니다")
    @GetMapping("/internal/v1/orders/{orderId}/review-eligibility")
    ApiResponse<InternalOrderReviewEligibilityResponse> getReviewEligibility(
            @PathVariable Long orderId,
            @RequestParam Long userId,
            @RequestParam Long itemId
    );
}
