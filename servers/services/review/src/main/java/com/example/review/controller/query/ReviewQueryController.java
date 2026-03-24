package com.example.review.controller.query;

import com.example.api.response.ApiResponse;
import com.example.review.controller.api.query.ReviewQueryApi;
import com.example.review.dto.response.ReviewResponse;
import com.example.review.service.query.ReviewQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReviewQueryController implements ReviewQueryApi {

    private final ReviewQueryService reviewQueryService;

    @Override
    public ApiResponse<Page<ReviewResponse>> getReviewsByItem(
            @PathVariable Long itemId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(reviewQueryService.getReviewsByItem(itemId, pageable));
    }
}
