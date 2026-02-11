package com.example.product.controller.command;

import com.example.api.response.ApiResponse;
import com.example.product.controller.api.command.GoodsCommandApi;
import com.example.product.dto.goods.request.GoodsCreateRequest;
import com.example.product.dto.goods.request.GoodsUpdateRequest;
import com.example.product.dto.goods.response.GoodsDetailResponse;
import com.example.product.service.command.GoodsCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/goods")
@RequiredArgsConstructor
public class GoodsCommandController implements GoodsCommandApi {

    private final GoodsCommandService goodsCommandService;

    @Override
    public ApiResponse<GoodsDetailResponse> create(GoodsCreateRequest request, Long sellerId) {
        return ApiResponse.success(goodsCommandService.createGoods(request, sellerId));
    }

    @Override
    public ApiResponse<GoodsDetailResponse> update(Long itemId, GoodsUpdateRequest request, Long sellerId) {
        return ApiResponse.success(goodsCommandService.updateGoods(itemId, request, sellerId));
    }

    @Override
    public ApiResponse<Void> delete(Long itemId, Long sellerId) {
        goodsCommandService.delete(itemId, sellerId);
        return ApiResponse.success();
    }
}
