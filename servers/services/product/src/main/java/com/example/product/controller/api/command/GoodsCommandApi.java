package com.example.product.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.product.dto.goods.request.GoodsCreateRequest;
import com.example.product.dto.goods.request.GoodsUpdateRequest;
import com.example.product.dto.goods.response.GoodsDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Goods Command", description = "굿즈 관리 API (쓰기)")
public interface GoodsCommandApi {

    @Operation(summary = "굿즈 등록")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    @PostMapping
    ApiResponse<GoodsDetailResponse> create(
            @Valid @RequestBody GoodsCreateRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long sellerId);

    @Operation(summary = "굿즈 수정")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "수정 불가 상태"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상품 없음")
    })
    @PutMapping("/{itemId}")
    ApiResponse<GoodsDetailResponse> update(
            @PathVariable Long itemId,
            @Valid @RequestBody GoodsUpdateRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long sellerId);

    @Operation(summary = "굿즈 삭제")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상품 없음")
    })
    @DeleteMapping("/{itemId}")
    ApiResponse<Void> delete(
            @PathVariable Long itemId,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long sellerId);
}
