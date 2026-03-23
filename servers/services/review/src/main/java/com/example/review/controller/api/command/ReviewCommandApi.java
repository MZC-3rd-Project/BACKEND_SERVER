package com.example.review.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.review.dto.request.CreateReviewRequest;
import com.example.review.dto.response.ReviewResponse;
import com.example.security.gateway.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Review Command", description = "리뷰 생성 API")
public interface ReviewCommandApi {

    @Operation(summary = "리뷰 생성", description = "구매 완료 주문에 대한 리뷰를 작성합니다")
    @PostMapping("/api/v1/reviews")
    ApiResponse<ReviewResponse> createReview(
            @Valid @RequestBody CreateReviewRequest request,
            @CurrentUserId Long userId
    );
}
