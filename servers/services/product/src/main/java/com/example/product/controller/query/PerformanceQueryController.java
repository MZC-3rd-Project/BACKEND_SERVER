package com.example.product.controller.query;

import com.example.api.response.ApiResponse;
import com.example.core.pagination.CursorResponse;
import com.example.product.controller.api.query.PerformanceQueryApi;
import com.example.product.dto.performance.response.PerformanceDetailResponse;
import com.example.product.dto.performance.response.PerformanceListResponse;
import com.example.product.service.query.PerformanceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/performances")
@RequiredArgsConstructor
public class PerformanceQueryController implements PerformanceQueryApi {

    private final PerformanceQueryService performanceQueryService;

    @Override
    public ApiResponse<PerformanceDetailResponse> findById(Long itemId) {
        return ApiResponse.success(performanceQueryService.findById(itemId));
    }

    @Override
    public ApiResponse<CursorResponse<PerformanceListResponse>> findList(String cursor, int size) {
        return ApiResponse.success(performanceQueryService.findList(cursor, size));
    }
}
