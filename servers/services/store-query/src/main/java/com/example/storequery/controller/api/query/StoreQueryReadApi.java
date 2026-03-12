package com.example.storequery.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.core.pagination.CursorResponse;
import com.example.security.gateway.CurrentUserId;
import com.example.storequery.dto.response.StoreQueryDetailResponse;
import com.example.storequery.dto.response.StoreQueryListResponse;
import com.example.storequery.entity.StoreQueryStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "Store Query", description = "store-query read model 조회 API")
public interface StoreQueryReadApi {

    @Operation(summary = "스토어 목록 조회")
    @GetMapping("/stores")
    ApiResponse<CursorResponse<StoreQueryListResponse>> getStores(
        @RequestParam(name = "q", required = false) String keyword,
        @RequestParam(name = "status", required = false) StoreQueryStatus status,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "20") int size
    );

    @Operation(summary = "스토어 상세 조회")
    @GetMapping("/stores/{storeId}")
    ApiResponse<StoreQueryDetailResponse> getStoreDetail(@PathVariable Long storeId);

    @Operation(summary = "현재 사용자 스토어 목록 조회")
    @GetMapping("/stores/me")
    ApiResponse<List<StoreQueryListResponse>> getMyStores(@CurrentUserId Long userId);

    @Operation(summary = "특정 사용자 스토어 목록 조회")
    @GetMapping("/users/{userId}/stores")
    ApiResponse<List<StoreQueryListResponse>> getUserStores(@PathVariable Long userId);
}
