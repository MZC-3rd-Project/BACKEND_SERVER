package com.example.hotdeal.controller.api;

import com.example.api.response.ApiResponse;
import com.example.hotdeal.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Hot-Deal Command", description = "핫딜 구매/대기열 API")
public interface HotDealCommandApi {

    @Operation(summary = "핫딜 수동 생성 (판매자)")
    @PostMapping
    ApiResponse<HotDealDetailResponse> createHotDeal(
            @Valid @RequestBody CreateHotDealRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId);

    @Operation(summary = "선착순 구매")
    @PostMapping("/{hotDealId}/purchase")
    ApiResponse<HotDealPurchaseResponse> purchase(
            @PathVariable Long hotDealId,
            @Valid @RequestBody HotDealPurchaseRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId);

    @Operation(summary = "대기열 진입")
    @PostMapping("/{hotDealId}/queue/enter")
    ApiResponse<QueueEnterResponse> enterQueue(
            @PathVariable Long hotDealId,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId);

    @Operation(summary = "대기열 상태 조회")
    @GetMapping("/{hotDealId}/queue")
    ApiResponse<QueueStatusResponse> getQueueStatus(
            @PathVariable Long hotDealId,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId);
}
