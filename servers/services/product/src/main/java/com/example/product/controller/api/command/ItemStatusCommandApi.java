package com.example.product.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.product.dto.item.request.StatusChangeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Item Status Command", description = "상품 상태 관리 API (쓰기)")
public interface ItemStatusCommandApi {

    @Operation(summary = "상태 변경")
    @PatchMapping("/{itemId}/status")
    ApiResponse<Void> changeStatus(
            @PathVariable Long itemId,
            @Valid @RequestBody StatusChangeRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId);
}
