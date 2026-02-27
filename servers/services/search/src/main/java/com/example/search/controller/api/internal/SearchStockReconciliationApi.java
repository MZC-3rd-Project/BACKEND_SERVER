package com.example.search.controller.api.internal;

import com.example.api.response.ApiResponse;
import com.example.search.dto.reconciliation.response.StockReconciliationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Search Internal Reconciliation", description = "검색 재고 정합성 점검 내부 API")
public interface SearchStockReconciliationApi {

    @Operation(summary = "itemId 기준 재고 정합성 점검")
    @GetMapping("/stock/{itemId}")
    ApiResponse<StockReconciliationResponse> reconcileStock(@PathVariable Long itemId);
}
