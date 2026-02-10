package com.example.product.controller.command;

import com.example.api.response.ApiResponse;
import com.example.product.controller.api.command.ItemStatusCommandApi;
import com.example.product.dto.item.request.StatusChangeRequest;
import com.example.product.service.command.ItemStatusCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemStatusCommandController implements ItemStatusCommandApi {

    private final ItemStatusCommandService itemStatusCommandService;

    @Override
    public ApiResponse<Void> changeStatus(Long itemId, StatusChangeRequest request, Long userId) {
        itemStatusCommandService.changeStatus(itemId, request, userId);
        return ApiResponse.success();
    }
}
