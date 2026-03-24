package com.example.order.controller.query;

import com.example.api.response.ApiResponse;
import com.example.order.controller.api.query.InternalOrderQueryApi;
import com.example.order.dto.response.InternalOrderDetailResponse;
import com.example.order.dto.response.InternalOrderReviewEligibilityResponse;
import com.example.order.service.query.OrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InternalOrderQueryController implements InternalOrderQueryApi {

    private final OrderQueryService orderQueryService;

    @Override
    public ApiResponse<InternalOrderDetailResponse> getOrder(@PathVariable Long orderId) {
        return ApiResponse.success(orderQueryService.getOrder(orderId));
    }

    @Override
    public ApiResponse<InternalOrderReviewEligibilityResponse> getReviewEligibility(
            @PathVariable Long orderId,
            @RequestParam Long userId,
            @RequestParam Long itemId
    ) {
        return ApiResponse.success(orderQueryService.getReviewEligibility(orderId, userId, itemId));
    }
}
