package com.example.hotdeal.controller.api;

import com.example.api.response.ApiResponse;
import com.example.hotdeal.dto.HotDealDetailResponse;
import com.example.hotdeal.dto.HotDealListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "Hot-Deal Query", description = "핫딜 조회 API")
public interface HotDealQueryApi {

    @Operation(summary = "핫딜 목록 조회 (ACTIVE)")
    @GetMapping
    ApiResponse<List<HotDealListResponse>> getActiveDeals(
            @Parameter(description = "커서 ID") @RequestParam(required = false) Long cursor,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size);

    @Operation(summary = "핫딜 상세 조회")
    @GetMapping("/{hotDealId}")
    ApiResponse<HotDealDetailResponse> getDetail(@PathVariable Long hotDealId);
}
