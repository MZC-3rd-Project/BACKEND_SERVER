package com.example.product.controller.query;

import com.example.api.response.ApiResponse;
import com.example.product.controller.api.query.SellerProductQueryApi;
import com.example.product.dto.goods.response.GoodsDetailResponse;
import com.example.product.service.query.ProductQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller/products")
@RequiredArgsConstructor
public class SellerProductQueryController implements SellerProductQueryApi {

    private final ProductQueryService productQueryService;

    @Override
    public ApiResponse<GoodsDetailResponse> findSellerById(Long itemId, Long sellerId) {
        return ApiResponse.success(productQueryService.findSellerProductById(itemId, sellerId));
    }
}

