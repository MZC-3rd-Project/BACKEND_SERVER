package com.example.product.controller.query;

import com.example.api.response.ApiResponse;
import com.example.product.controller.api.query.SellerGoodsQueryApi;
import com.example.product.dto.goods.response.GoodsDetailResponse;
import com.example.product.service.query.GoodsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller/goods")
@RequiredArgsConstructor
public class SellerGoodsQueryController implements SellerGoodsQueryApi {

    private final GoodsQueryService goodsQueryService;

    @Override
    public ApiResponse<GoodsDetailResponse> findSellerById(Long itemId, Long sellerId) {
        return ApiResponse.success(goodsQueryService.findSellerGoodsById(itemId, sellerId));
    }
}

