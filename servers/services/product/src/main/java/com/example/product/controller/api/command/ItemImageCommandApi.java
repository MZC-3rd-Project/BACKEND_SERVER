package com.example.product.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.product.dto.image.request.ItemImageRequest;
import com.example.product.dto.image.response.ItemImageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Item Image Command", description = "상품 이미지 관리 API (쓰기)")
public interface ItemImageCommandApi {

    @Operation(summary = "이미지 추가 (다건)")
    @PostMapping("/{itemId}/images")
    ApiResponse<List<ItemImageResponse>> addImages(
            @PathVariable Long itemId,
            @Valid @RequestBody List<ItemImageRequest> requests,
            @RequestHeader("X-User-Id") Long userId);

    @Operation(summary = "이미지 삭제")
    @DeleteMapping("/images/{imageId}")
    ApiResponse<Void> deleteImage(
            @PathVariable Long imageId,
            @RequestHeader("X-User-Id") Long userId);

    @Operation(summary = "이미지 순서 변경")
    @PutMapping("/{itemId}/images/reorder")
    ApiResponse<List<ItemImageResponse>> reorder(
            @PathVariable Long itemId,
            @RequestBody List<Long> imageIds,
            @RequestHeader("X-User-Id") Long userId);
}
