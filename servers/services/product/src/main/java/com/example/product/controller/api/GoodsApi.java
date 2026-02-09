package com.example.product.controller.api;

import com.example.api.response.ApiResponse;
import com.example.product.dto.goods.GoodsCreateRequest;
import com.example.product.dto.goods.GoodsDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Goods", description = "굿즈 관리 API")
public interface GoodsApi {

    @Operation(summary = "굿즈 등록", description = "공연 연결 가능, 옵션/배송정보 선택")
    @PostMapping
    ApiResponse<GoodsDetailResponse> create(
            @Valid @RequestBody GoodsCreateRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long sellerId);

    @Operation(summary = "굿즈 상세 조회")
    @GetMapping("/{itemId}")
    ApiResponse<GoodsDetailResponse> findById(@PathVariable Long itemId);

    @Operation(summary = "굿즈 목록 조회", description = "커서 기반 페이징")
    @GetMapping
    ApiResponse<List<GoodsDetailResponse>> findList(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size);
}
