package com.example.stock.controller.query;

import com.example.api.response.ApiResponse;
import com.example.stock.controller.api.query.StockQueryApi;
import com.example.stock.dto.query.response.StockHistoryQueryResponse;
import com.example.stock.dto.query.response.StockItemQueryResponse;
import com.example.stock.dto.query.response.StockReservationQueryResponse;
import com.example.stock.dto.query.response.StockSummaryQueryResponse;
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
    public ApiResponse<StockItemQueryResponse> getStock(@PathVariable Long stockItemId) {
        return ApiResponse.success(stockQueryService.getStock(stockItemId));
    }

    @Override
    public ApiResponse<StockSummaryQueryResponse> getStocksByItemId(@PathVariable Long itemId) {
        return ApiResponse.success(stockQueryService.getStocksByItemId(itemId));
    }

    @Override
    public ApiResponse<List<StockHistoryQueryResponse>> getStockHistory(
            @PathVariable Long stockItemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(stockQueryService.getStockHistory(stockItemId, page, size));
    }

    @Override
    public ApiResponse<List<StockReservationQueryResponse>> getReservationsByOrderId(@RequestParam Long orderId) {
        return ApiResponse.success(stockQueryService.getReservationsByOrderId(orderId));
    }
}
