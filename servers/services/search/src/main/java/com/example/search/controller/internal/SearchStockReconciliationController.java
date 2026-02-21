package com.example.search.controller.internal;

import com.example.api.response.ApiResponse;
import com.example.search.controller.api.internal.SearchStockReconciliationApi;
import com.example.search.dto.reconciliation.response.StockReconciliationResponse;
import com.example.search.service.reconciliation.StockReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/search/reconciliation")
@RequiredArgsConstructor
public class SearchStockReconciliationController implements SearchStockReconciliationApi {

    private final StockReconciliationService stockReconciliationService;

    @Override
    public ApiResponse<StockReconciliationResponse> reconcileStock(@PathVariable Long itemId) {
        return ApiResponse.success(stockReconciliationService.reconcileItem(itemId));
    }
}
