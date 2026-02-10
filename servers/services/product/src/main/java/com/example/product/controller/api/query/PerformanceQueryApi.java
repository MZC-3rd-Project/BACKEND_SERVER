package com.example.product.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.core.pagination.CursorResponse;
import com.example.product.dto.performance.response.PerformanceDetailResponse;
import com.example.product.dto.performance.response.PerformanceListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Performance Query", description = "공연 조회 API (읽기)")
public interface PerformanceQueryApi {

    @Operation(summary = "공연 상세 조회")
    @GetMapping("/{itemId}")
    ApiResponse<PerformanceDetailResponse> findById(@PathVariable Long itemId);

    @Operation(summary = "공연 목록 조회")
    @GetMapping
    ApiResponse<CursorResponse<PerformanceListResponse>> findList(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size);
}
