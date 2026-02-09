package com.example.product.controller.api;

import com.example.api.response.ApiResponse;
import com.example.product.dto.goods.GoodsDetailResponse;
import com.example.product.dto.goods.ProductCreateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Product", description = "일반상품 관리 API")
public interface ProductItemApi {

    @Operation(summary = "일반상품 등록", description = "배송 정보 필수")
    @PostMapping
    ApiResponse<GoodsDetailResponse> create(
            @Valid @RequestBody ProductCreateRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long sellerId);

    @Operation(summary = "일반상품 상세 조회")
    @GetMapping("/{itemId}")
    ApiResponse<GoodsDetailResponse> findById(@PathVariable Long itemId);

    @Operation(summary = "일반상품 목록 조회", description = "커서 기반 페이징")
    @GetMapping
    ApiResponse<List<GoodsDetailResponse>> findList(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size);
}
