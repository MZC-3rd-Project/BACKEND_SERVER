package com.example.product.controller;

import com.example.api.response.ApiResponse;
import com.example.product.controller.api.ItemStatusApi;
import com.example.product.dto.item.StatusChangeRequest;
import com.example.product.dto.item.StatusHistoryResponse;
import com.example.product.service.ItemStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemStatusController implements ItemStatusApi {

    private final ItemStatusService itemStatusService;

    @Override
    public ApiResponse<Void> changeStatus(Long itemId, StatusChangeRequest request, Long userId) {
        itemStatusService.changeStatus(itemId, request, userId);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<List<StatusHistoryResponse>> getHistory(Long itemId) {
        return ApiResponse.success(itemStatusService.getHistory(itemId));
    }
}
