package com.example.review.controller.command;

import com.example.api.response.ApiResponse;
import com.example.review.controller.api.command.ReviewCommandApi;
import com.example.review.dto.request.CreateReviewRequest;
import com.example.review.dto.response.ReviewResponse;
import com.example.review.service.command.ReviewCommandService;
import com.example.security.gateway.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReviewCommandController implements ReviewCommandApi {

    private final ReviewCommandService reviewCommandService;

    @Override
    public ApiResponse<ReviewResponse> createReview(CreateReviewRequest request, @CurrentUserId Long userId) {
        return ApiResponse.success(reviewCommandService.createReview(request, userId));
    }
}
