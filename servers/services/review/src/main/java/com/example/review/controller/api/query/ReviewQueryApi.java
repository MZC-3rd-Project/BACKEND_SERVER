package com.example.review.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.review.dto.response.ReviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Review Query", description = "리뷰 조회 API")
public interface ReviewQueryApi {

    @Operation(summary = "상품별 리뷰 목록 조회", description = "상품 기준 리뷰를 최신순으로 조회합니다")
    @GetMapping("/api/v1/reviews/items/{itemId}")
    ApiResponse<Page<ReviewResponse>> getReviewsByItem(@PathVariable Long itemId, Pageable pageable);
}
