package com.example.stock.controller.query;

import com.example.api.response.ApiResponse;
import com.example.stock.controller.api.query.StockQueryApi;
import com.example.stock.dto.response.StockHistoryResponse;
import com.example.stock.dto.response.StockResponse;
import com.example.stock.dto.response.StockSummaryResponse;
import com.example.stock.service.query.StockQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class StockQueryController implements StockQueryApi {

    private final StockQueryService stockQueryService;

    @Override
    public ApiResponse<StockResponse> getStock(@PathVariable Long stockItemId) {
        return ApiResponse.success(stockQueryService.getStock(stockItemId));
    }

    @Override
    public ApiResponse<StockSummaryResponse> getStocksByItemId(@PathVariable Long itemId) {
        return ApiResponse.success(stockQueryService.getStocksByItemId(itemId));
    }

    @Override
    public ApiResponse<List<StockHistoryResponse>> getStockHistory(
            @PathVariable Long stockItemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(stockQueryService.getStockHistory(stockItemId, page, size));
    }
}
