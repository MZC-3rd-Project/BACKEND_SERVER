package com.example.hotdeal.controller;

import com.example.api.response.ApiResponse;
import com.example.hotdeal.controller.api.HotDealQueryApi;
import com.example.hotdeal.dto.HotDealDetailResponse;
import com.example.hotdeal.dto.HotDealListResponse;
import com.example.hotdeal.service.HotDealQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/hot-deals")
@RequiredArgsConstructor
public class HotDealQueryController implements HotDealQueryApi {

    private final HotDealQueryService hotDealQueryService;

    @Override
    public ApiResponse<List<HotDealListResponse>> getActiveDeals(Long cursor, int size) {
        return ApiResponse.success(hotDealQueryService.getActiveDeals(cursor, size));
    }

    @Override
    public ApiResponse<HotDealDetailResponse> getDetail(Long hotDealId) {
        return ApiResponse.success(hotDealQueryService.getDetail(hotDealId));
    }
}
