package com.example.hotdeal.controller;

import com.example.api.response.ApiResponse;
import com.example.hotdeal.controller.api.HotDealCommandApi;
import com.example.hotdeal.dto.*;
import com.example.hotdeal.dto.UpdateHotDealRequest;
import com.example.hotdeal.service.HotDealCommandService;
import com.example.hotdeal.service.HotDealPurchaseService;
import com.example.hotdeal.service.QueueSseEventPublisher;
import com.example.hotdeal.service.QueueSseService;
import com.example.hotdeal.service.QueueService;
import com.example.hotdeal.service.checkout.HotDealCheckoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/api/v1/hot-deals")
@RequiredArgsConstructor
public class HotDealCommandController implements HotDealCommandApi {

    private final HotDealCommandService hotDealCommandService;
    private final HotDealPurchaseService hotDealPurchaseService;
    private final HotDealCheckoutService hotDealCheckoutService;
    private final QueueService queueService;
    private final QueueSseService queueSseService;
    private final QueueSseEventPublisher queueSseEventPublisher;

    @Override
    public ApiResponse<HotDealDetailResponse> createHotDeal(@Valid @RequestBody CreateHotDealRequest request,
                                                            @RequestHeader("X-User-Id") Long userId) {
        log.info("request : {}, userId : {}",request, userId);
        return ApiResponse.success(hotDealCommandService.createManual(request, userId));
    }

    @Override
    public ApiResponse<HotDealPurchaseResponse> purchase(@PathVariable Long hotDealId,
                                                         @Valid @RequestBody HotDealPurchaseRequest request,
                                                         @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success(hotDealPurchaseService.purchase(hotDealId, request, userId));
    }

    @Override
    public ApiResponse<HotDealCheckoutReserveResponse> reserveCheckout(@PathVariable Long hotDealId,
                                                                       @Valid @RequestBody HotDealCheckoutReserveRequest request,
                                                                       @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success(hotDealCheckoutService.reserve(hotDealId, request, userId));
    }

    @Override
    public ApiResponse<HotDealCheckoutSubmitResponse> submitCheckout(@Valid @RequestBody HotDealCheckoutSubmitRequest request,
                                                                     @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success(hotDealCheckoutService.submit(request, userId));
    }

    @Override
    public ApiResponse<HotDealCheckoutCancelResponse> cancelCheckout(@Valid @RequestBody HotDealCheckoutCancelRequest request,
                                                                     @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success(hotDealCheckoutService.cancel(request, userId));
    }

    @Override
    public ApiResponse<QueueEnterResponse> enterQueue(@PathVariable Long hotDealId,
                                                      @RequestHeader("X-User-Id") Long userId) {
        QueueEnterResponse response = queueService.enter(hotDealId, userId);
        boolean canPurchase = response.getPosition() != null && response.getPosition() == 0L;
        queueSseEventPublisher.publishQueueStatus(hotDealId, userId, response.getPosition(), canPurchase);
        return ApiResponse.success(response);
    }

    @Override
    public ApiResponse<QueueStatusResponse> getQueueStatus(@PathVariable Long hotDealId,
                                                           @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success(queueService.getStatus(hotDealId, userId));
    }

    @Override
    public SseEmitter streamQueue(@PathVariable Long hotDealId,
                                  @RequestHeader("X-User-Id") Long userId) {
        return queueSseService.subscribe(hotDealId, userId);
    }

    @Override
    public ApiResponse<HotDealDetailResponse> updateHotDeal(Long hotDealId, UpdateHotDealRequest request) {
        return ApiResponse.success(hotDealCommandService.update(hotDealId, request));
    }

    @Override
    public ApiResponse<Void> deleteHotDeal(Long hotDealId) {
        hotDealCommandService.delete(hotDealId);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<HotDealDetailResponse> restoreHotDeal(Long hotDealId) {
        return ApiResponse.success(hotDealCommandService.restore(hotDealId));
    }
}
