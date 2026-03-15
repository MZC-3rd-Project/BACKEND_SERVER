package com.example.store.controller.query;

import com.example.api.response.ApiResponse;
import com.example.store.controller.api.query.StoreQueryApi;
import com.example.store.dto.response.StoreDetailResponse;
import com.example.store.dto.response.StoreListResponse;
import com.example.store.service.query.StoreQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.data.domain.Pageable;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
public class StoreQueryController implements StoreQueryApi {

    private final StoreQueryService storeQueryService;

    @Override
    public ApiResponse<List<StoreListResponse>> getMyStore(Long userId) {
        return ApiResponse.success(storeQueryService.getMyStoreList(userId));
    }

    @Override
    public ApiResponse<Page<StoreListResponse>> getStoreQueryList(Pageable pageable) {
        return ApiResponse.success(storeQueryService.getStoreListInfo(pageable));
    }

    @Override
    public ApiResponse<StoreDetailResponse> getStoreQueryDetail(@PathVariable Long storeId) {
        return ApiResponse.success(storeQueryService.getStoreDetail(storeId));
    }


}
