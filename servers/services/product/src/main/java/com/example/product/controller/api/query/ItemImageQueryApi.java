package com.example.product.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.product.dto.image.response.ItemImageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Tag(name = "Item Image Query", description = "상품 이미지 조회 API (읽기)")
public interface ItemImageQueryApi {

    @Operation(summary = "상품 이미지 목록 조회")
    @GetMapping("/{itemId}/images")
    ApiResponse<List<ItemImageResponse>> findByItemId(@PathVariable Long itemId);
}
