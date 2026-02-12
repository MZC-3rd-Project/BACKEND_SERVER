package com.example.sales.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.core.pagination.CursorResponse;
import com.example.sales.dto.response.PurchaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Purchase Query", description = "구매 내역 조회 API (읽기)")
public interface PurchaseQueryApi {

    @Operation(summary = "내 구매 내역 목록 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping
    ApiResponse<CursorResponse<PurchaseResponse>> findMyPurchases(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size);

    @Operation(summary = "구매 상세 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "구매 없음")
    })
    @GetMapping("/{purchaseId}")
    ApiResponse<PurchaseResponse> findPurchaseDetail(
            @PathVariable Long purchaseId,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId);
}
