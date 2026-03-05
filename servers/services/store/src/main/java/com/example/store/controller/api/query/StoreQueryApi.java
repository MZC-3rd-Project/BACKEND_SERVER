package com.example.store.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.store.entity.Stores;
import io.lettuce.core.dynamic.annotation.Param;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name ="store 조회 api",description = "store info 조회")
public interface StoreQueryApi {

    @Operation(description = "유저가 등록한 store 관리자페이지")
    @GetMapping
    ApiResponse<?> getMyStore(
        @RequestHeader("X-User-Id") Long userId);
}
