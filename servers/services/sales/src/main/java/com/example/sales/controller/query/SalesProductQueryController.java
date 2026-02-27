package com.example.sales.controller.query;

import com.example.api.response.ApiResponse;
import com.example.core.pagination.CursorResponse;
import com.example.sales.controller.api.query.SalesProductQueryApi;
import com.example.sales.dto.response.SalesProductDetailResponse;
import com.example.sales.dto.response.SalesProductListItemResponse;
import com.example.sales.service.query.SalesProductQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
public class SalesProductQueryController implements SalesProductQueryApi {

    private final SalesProductQueryService salesProductQueryService;

    @Override
    public ApiResponse<CursorResponse<SalesProductListItemResponse>> findProducts(String cursor, int size) {
        return ApiResponse.success(salesProductQueryService.findProducts(cursor, size));
    }

    @Override
    public ApiResponse<SalesProductDetailResponse> findProductDetail(Long saleId) {
        return ApiResponse.success(salesProductQueryService.findProductDetail(saleId));
    }
}
