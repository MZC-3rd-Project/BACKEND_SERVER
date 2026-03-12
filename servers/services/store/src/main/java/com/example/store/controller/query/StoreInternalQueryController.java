package com.example.store.controller.query;

import com.example.api.response.ApiResponse;
import com.example.store.controller.api.query.StoreInternalQueryApi;
import com.example.store.dto.response.internal.StoreSnapshotResponse;
import com.example.store.service.query.StoreQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/stores")
@RequiredArgsConstructor
public class StoreInternalQueryController implements StoreInternalQueryApi {

    private final StoreQueryService storeQueryService;

    @Override
    public ApiResponse<StoreSnapshotResponse> getStoreSnapshot(Long storeId) {
        return ApiResponse.success(storeQueryService.getStoreSnapshot(storeId));
    }

    @Override
    public ApiResponse<List<Long>> getActiveStoreIdsByUserId(Long userId) {
        return ApiResponse.success(storeQueryService.getActiveStoreIdsByUserId(userId));
    }
}
