package com.example.storequery.controller.query;

import com.example.api.response.ApiResponse;
import com.example.core.pagination.CursorResponse;
import com.example.storequery.controller.api.query.StoreQueryReadApi;
import com.example.storequery.dto.response.StoreQueryDetailResponse;
import com.example.storequery.dto.response.StoreQueryListResponse;
import com.example.storequery.entity.StoreQueryStatus;
import com.example.storequery.service.query.StoreQueryReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/store-query")
@RequiredArgsConstructor
public class StoreQueryReadController implements StoreQueryReadApi {

    private final StoreQueryReadService storeQueryReadService;

    @Override
    public ApiResponse<CursorResponse<StoreQueryListResponse>> getStores(String keyword, StoreQueryStatus status, String cursor, int size) {
        return ApiResponse.success(storeQueryReadService.getStores(keyword, status, cursor, size));
    }

    @Override
    public ApiResponse<StoreQueryDetailResponse> getStoreDetail(Long storeId) {
        return ApiResponse.success(storeQueryReadService.getStoreDetail(storeId));
    }

    @Override
    public ApiResponse<List<StoreQueryListResponse>> getMyStores(Long userId) {
        return ApiResponse.success(storeQueryReadService.getMyStores(userId));
    }

    @Override
    public ApiResponse<List<StoreQueryListResponse>> getUserStores(Long userId) {
        return ApiResponse.success(storeQueryReadService.getStoresByUserId(userId));
    }
}
