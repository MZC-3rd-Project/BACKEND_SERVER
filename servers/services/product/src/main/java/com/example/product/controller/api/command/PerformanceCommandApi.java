package com.example.product.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.product.dto.performance.request.PerformanceCreateRequest;
import com.example.product.dto.performance.request.PerformanceUpdateRequest;
import com.example.product.dto.performance.response.PerformanceDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Performance Command", description = "공연 관리 API (쓰기)")
public interface PerformanceCommandApi {

    @Operation(summary = "공연 등록")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    @PostMapping
    ApiResponse<PerformanceDetailResponse> create(
            @Valid @RequestBody PerformanceCreateRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long sellerId);

    @Operation(summary = "공연 수정")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "공연/상품 없음")
    })
    @PutMapping("/{itemId}")
    ApiResponse<PerformanceDetailResponse> update(
            @PathVariable Long itemId,
            @Valid @RequestBody PerformanceUpdateRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long sellerId);

    @Operation(summary = "공연 삭제")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "공연/상품 없음")
    })
    @DeleteMapping("/{itemId}")
    ApiResponse<Void> delete(
            @PathVariable Long itemId,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long sellerId);
}
