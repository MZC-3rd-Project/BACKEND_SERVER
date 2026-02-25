package com.example.sales.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.core.pagination.CursorResponse;
import com.example.sales.dto.response.SalesProductDetailResponse;
import com.example.sales.dto.response.SalesProductListItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Sales Product Query", description = "일반 판매 상품 조회 API (읽기)")
public interface SalesProductQueryApi {

    @Operation(summary = "일반 판매 상품 목록 조회")
    @GetMapping("/products")
    ApiResponse<CursorResponse<SalesProductListItemResponse>> findProducts(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    );

    @Operation(summary = "일반 판매 상품 상세 조회")
    @GetMapping("/products/{saleId}")
    ApiResponse<SalesProductDetailResponse> findProductDetail(@PathVariable Long saleId);
}
