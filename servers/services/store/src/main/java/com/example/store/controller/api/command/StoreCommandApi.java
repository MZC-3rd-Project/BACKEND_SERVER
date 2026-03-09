package com.example.store.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.store.dto.request.StoreCreateRequest;
import com.example.store.dto.request.StoreUpdateRequest;
import com.example.store.dto.response.StoreCreateResponse;
import com.example.store.dto.response.StoreUpdateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "store command api, cud",description = "store command api")
public interface StoreCommandApi {

    @Operation(description = "유저가 store 개설")
    @PostMapping
    ApiResponse<StoreCreateResponse> createStore(
        @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId,
        @Valid @RequestBody StoreCreateRequest request
        );

    @Operation(description = "Store 수정 (부분 수정 가능)")
    @PatchMapping("/{storeId}")
    ApiResponse<StoreUpdateResponse> updateStore(
        @Parameter(hidden = true)
        @RequestHeader("X-User-Id") Long userId,
        @PathVariable Long storeId,
        @Valid @RequestBody StoreUpdateRequest request
    );
}
