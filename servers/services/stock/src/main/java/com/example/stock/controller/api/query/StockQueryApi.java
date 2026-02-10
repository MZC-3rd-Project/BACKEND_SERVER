package com.example.stock.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.stock.dto.response.StockResponse;
import com.example.stock.dto.response.StockSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Stock Query", description = "재고 조회 내부 API")
public interface StockQueryApi {

    @Operation(summary = "재고 단건 조회")
    @GetMapping("/internal/v1/stock/{stockItemId}")
    ApiResponse<StockResponse> getStock(@PathVariable Long stockItemId);

    @Operation(summary = "아이템별 재고 목록 조회")
    @GetMapping("/internal/v1/stock/items/{itemId}")
    ApiResponse<StockSummaryResponse> getStocksByItemId(@PathVariable Long itemId);
}
