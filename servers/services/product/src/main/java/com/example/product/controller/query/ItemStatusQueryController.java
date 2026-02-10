package com.example.product.controller.query;

import com.example.api.response.ApiResponse;
import com.example.product.controller.api.query.ItemStatusQueryApi;
import com.example.product.dto.item.response.StatusHistoryResponse;
import com.example.product.service.query.ItemStatusQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemStatusQueryController implements ItemStatusQueryApi {

    private final ItemStatusQueryService itemStatusQueryService;

    @Override
    public ApiResponse<List<StatusHistoryResponse>> getHistory(Long itemId) {
        return ApiResponse.success(itemStatusQueryService.getHistory(itemId));
    }
}
