package com.example.product.controller.command;

import com.example.api.response.ApiResponse;
import com.example.product.controller.api.command.PerformanceCommandApi;
import com.example.product.dto.performance.request.PerformanceCreateRequest;
import com.example.product.dto.performance.request.PerformanceUpdateRequest;
import com.example.product.dto.performance.response.PerformanceDetailResponse;
import com.example.product.service.command.PerformanceCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/performances")
@RequiredArgsConstructor
public class PerformanceCommandController implements PerformanceCommandApi {

    private final PerformanceCommandService performanceCommandService;

    @Override
    public ApiResponse<PerformanceDetailResponse> create(PerformanceCreateRequest request, Long sellerId) {
        return ApiResponse.success(performanceCommandService.create(request, sellerId));
    }

    @Override
    public ApiResponse<PerformanceDetailResponse> update(Long itemId, PerformanceUpdateRequest request, Long sellerId) {
        return ApiResponse.success(performanceCommandService.update(itemId, request, sellerId));
    }

    @Override
    public ApiResponse<Void> delete(Long itemId, Long sellerId) {
        performanceCommandService.delete(itemId, sellerId);
        return ApiResponse.success();
    }
}
