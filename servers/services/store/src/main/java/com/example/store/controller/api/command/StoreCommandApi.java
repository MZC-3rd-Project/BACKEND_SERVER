package com.example.store.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.store.dto.request.StoreCreateRequest;
import com.example.store.dto.response.StoreCreateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "store command api, cud",description = "store command api")
public interface StoreCommandApi {

    @Operation(description = "유저가 store 개설")
    @PostMapping
    ApiResponse<StoreCreateResponse> createStore(
        @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId,
        @Valid @RequestBody StoreCreateRequest request
        );
}
