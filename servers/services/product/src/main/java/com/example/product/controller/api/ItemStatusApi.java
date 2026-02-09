package com.example.product.controller.api;

import com.example.api.response.ApiResponse;
import com.example.product.dto.item.StatusChangeRequest;
import com.example.product.dto.item.StatusHistoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Item Status", description = "상품 상태 관리 API")
public interface ItemStatusApi {

    @Operation(summary = "상태 변경", description = "유효한 상태 전이만 허용, 이력 저장 + 이벤트 발행")
    @PatchMapping("/{itemId}/status")
    ApiResponse<Void> changeStatus(
            @PathVariable Long itemId,
            @Valid @RequestBody StatusChangeRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId);

    @Operation(summary = "상태 변경 이력 조회")
    @GetMapping("/{itemId}/status-history")
    ApiResponse<List<StatusHistoryResponse>> getHistory(@PathVariable Long itemId);
}
