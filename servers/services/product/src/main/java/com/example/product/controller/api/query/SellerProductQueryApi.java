package com.example.product.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.product.dto.goods.response.GoodsDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Seller Product Query", description = "판매자 일반상품 조회 API (읽기)")
public interface SellerProductQueryApi {

    @Operation(summary = "판매자 일반상품 상세 조회")
    @GetMapping("/{itemId}")
    ApiResponse<GoodsDetailResponse> findSellerById(
            @PathVariable Long itemId,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long sellerId);
}

