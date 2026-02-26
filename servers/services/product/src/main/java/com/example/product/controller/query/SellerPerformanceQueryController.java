package com.example.product.controller.query;

import com.example.api.response.ApiResponse;
import com.example.product.controller.api.query.SellerPerformanceQueryApi;
import com.example.product.dto.performance.response.PerformanceDetailResponse;
import com.example.product.service.query.PerformanceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller/performances")
@RequiredArgsConstructor
public class SellerPerformanceQueryController implements SellerPerformanceQueryApi {

    private final PerformanceQueryService performanceQueryService;

    @Override
    public ApiResponse<PerformanceDetailResponse> findSellerById(Long itemId, Long sellerId) {
        return ApiResponse.success(performanceQueryService.findSellerById(itemId, sellerId));
    }
}

