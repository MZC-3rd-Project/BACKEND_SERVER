package com.example.product.controller.api;

import com.example.api.response.ApiResponse;
import com.example.product.dto.image.ItemImageRequest;
import com.example.product.dto.image.ItemImageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Item Image", description = "상품 이미지 관리 API")
public interface ItemImageApi {

    @Operation(summary = "이미지 추가 (다건)", description = "여러 이미지를 한 번에 등록")
    @PostMapping("/{itemId}/images")
    ApiResponse<List<ItemImageResponse>> addImages(@PathVariable Long itemId, @Valid @RequestBody List<ItemImageRequest> requests);

    @Operation(summary = "상품 이미지 목록 조회")
    @GetMapping("/{itemId}/images")
    ApiResponse<List<ItemImageResponse>> findByItemId(@PathVariable Long itemId);

    @Operation(summary = "이미지 삭제")
    @DeleteMapping("/images/{imageId}")
    ApiResponse<Void> deleteImage(@PathVariable Long imageId);

    @Operation(summary = "이미지 순서 변경", description = "imageId 리스트 순서대로 sortOrder 재설정")
    @PutMapping("/{itemId}/images/reorder")
    ApiResponse<List<ItemImageResponse>> reorder(@PathVariable Long itemId, @RequestBody List<Long> imageIds);
}
