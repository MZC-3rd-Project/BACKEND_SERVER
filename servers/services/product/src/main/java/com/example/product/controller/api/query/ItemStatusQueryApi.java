package com.example.product.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.product.dto.item.response.StatusHistoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Tag(name = "Item Status Query", description = "상품 상태 조회 API (읽기)")
public interface ItemStatusQueryApi {

    @Operation(summary = "상태 변경 이력 조회")
    @GetMapping("/{itemId}/status-history")
    ApiResponse<List<StatusHistoryResponse>> getHistory(@PathVariable Long itemId);
}
