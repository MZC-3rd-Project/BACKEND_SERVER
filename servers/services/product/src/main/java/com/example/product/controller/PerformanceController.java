package com.example.product.controller;

import com.example.api.response.ApiResponse;
import com.example.product.controller.api.PerformanceApi;
import com.example.product.dto.performance.*;
import com.example.product.service.PerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/performances")
@RequiredArgsConstructor
public class PerformanceController implements PerformanceApi {

    private final PerformanceService performanceService;

    @Override
    public ApiResponse<PerformanceDetailResponse> create(PerformanceCreateRequest request, Long sellerId) {
        return ApiResponse.success(performanceService.create(request, sellerId));
    }

    @Override
    public ApiResponse<PerformanceDetailResponse> findById(Long itemId) {
        return ApiResponse.success(performanceService.findById(itemId));
    }

    @Override
    public ApiResponse<List<PerformanceListResponse>> findList(Long cursor, int size) {
        return ApiResponse.success(performanceService.findList(cursor, size));
    }

    @Override
    public ApiResponse<PerformanceDetailResponse> update(Long itemId, PerformanceUpdateRequest request, Long sellerId) {
        return ApiResponse.success(performanceService.update(itemId, request, sellerId));
    }

    @Override
    public ApiResponse<Void> delete(Long itemId, Long sellerId) {
        performanceService.delete(itemId, sellerId);
        return ApiResponse.success();
    }
}
