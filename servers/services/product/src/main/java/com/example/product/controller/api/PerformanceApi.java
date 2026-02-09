package com.example.product.controller.api;

import com.example.api.response.ApiResponse;
import com.example.product.dto.performance.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Performance", description = "공연 등록/조회 API")
public interface PerformanceApi {

    @Operation(summary = "공연 등록", description = "DRAFT 상태로 공연 등록. 좌석등급 필수, 출연진 선택")
    @PostMapping
    ApiResponse<PerformanceDetailResponse> create(
            @Valid @RequestBody PerformanceCreateRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long sellerId);

    @Operation(summary = "공연 상세 조회", description = "좌석등급 + 출연진 포함")
    @GetMapping("/{itemId}")
    ApiResponse<PerformanceDetailResponse> findById(@PathVariable Long itemId);

    @Operation(summary = "공연 목록 조회", description = "커서 기반 페이징")
    @GetMapping
    ApiResponse<List<PerformanceListResponse>> findList(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size);

    @Operation(summary = "공연 수정", description = "본인이 등록한 공연만 수정 가능")
    @PutMapping("/{itemId}")
    ApiResponse<PerformanceDetailResponse> update(
            @PathVariable Long itemId,
            @Valid @RequestBody PerformanceUpdateRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long sellerId);

    @Operation(summary = "공연 삭제", description = "본인이 등록한 공연만 삭제 (soft delete)")
    @DeleteMapping("/{itemId}")
    ApiResponse<Void> delete(
            @PathVariable Long itemId,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long sellerId);
}
