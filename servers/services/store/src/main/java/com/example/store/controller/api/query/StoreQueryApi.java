package com.example.store.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.store.dto.response.StoreListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import org.springframework.data.domain.Pageable;

@Tag(name ="store 조회 api",description = "store info 조회")
public interface StoreQueryApi {

    @Operation(description = "유저가 등록한 store introduce")
    @GetMapping
    ApiResponse<?> getMyStore(
        @RequestHeader("X-User-Id") Long userId);

    @Operation(description = "store list 목록")
    @GetMapping("/store_list")
    ApiResponse<Page<StoreListResponse>> getStoreQueryList(
        Pageable pageable);

    @Operation(description = "store 상세 페이지")
    @GetMapping("/{storeId}")
    ApiResponse<?> getStoreQueryDetail(
        @PathVariable Long storeId
    );
}
