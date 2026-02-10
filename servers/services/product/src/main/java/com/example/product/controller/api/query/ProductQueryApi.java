package com.example.product.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.core.pagination.CursorResponse;
import com.example.product.dto.goods.response.GoodsDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Product Query", description = "일반상품 조회 API (읽기)")
public interface ProductQueryApi {

    @Operation(summary = "일반상품 상세 조회")
    @GetMapping("/{itemId}")
    ApiResponse<GoodsDetailResponse> findById(@PathVariable Long itemId);

    @Operation(summary = "일반상품 목록 조회")
    @GetMapping
    ApiResponse<CursorResponse<GoodsDetailResponse>> findList(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size);
}
