package com.example.store.controller.query;

import com.example.api.response.ApiResponse;
import com.example.store.controller.api.query.StoreQueryApi;
import com.example.store.dto.response.StoreListResponse;
import com.example.store.service.query.StoreQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.data.domain.Pageable;

@Slf4j
@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
public class StoreQueryController implements StoreQueryApi {

    private final StoreQueryService storeQueryService;

    @Override
    public ApiResponse<?> getMyStore(Long userId) {

        return null;
    }

    @Override
    public ApiResponse<Page<StoreListResponse>> getStoreQueryList(Pageable pageable) {
        return ApiResponse.success(storeQueryService.getStoreListInfo(pageable));
    }

    @Override
    public ApiResponse<?> getStoreQueryDetail(Long storeId) {
        return null;
    }


}
