package com.example.hotdeal.controller;

import com.example.api.response.ApiResponse;
import com.example.hotdeal.controller.api.HotDealCommandApi;
import com.example.hotdeal.dto.*;
import com.example.hotdeal.entity.HotDeal;
import com.example.hotdeal.service.HotDealCommandService;
import com.example.hotdeal.service.HotDealPurchaseService;
import com.example.hotdeal.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hot-deals")
@RequiredArgsConstructor
public class HotDealCommandController implements HotDealCommandApi {

    private final HotDealCommandService hotDealCommandService;
    private final HotDealPurchaseService hotDealPurchaseService;
    private final QueueService queueService;

    @Override
    public ApiResponse<HotDealDetailResponse> createHotDeal(CreateHotDealRequest request, Long userId) {
        return ApiResponse.success(hotDealCommandService.createManual(request, userId));
    }

    @Override
    public ApiResponse<HotDealPurchaseResponse> purchase(Long hotDealId,
                                                          HotDealPurchaseRequest request, Long userId) {
        return ApiResponse.success(hotDealPurchaseService.purchase(hotDealId, request, userId));
    }

    @Override
    public ApiResponse<QueueEnterResponse> enterQueue(Long hotDealId, Long userId) {
        return ApiResponse.success(queueService.enter(hotDealId, userId));
    }

    @Override
    public ApiResponse<QueueStatusResponse> getQueueStatus(Long hotDealId, Long userId) {
        return ApiResponse.success(queueService.getStatus(hotDealId, userId));
    }
}
