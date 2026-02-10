package com.example.product.controller.query;

import com.example.api.response.ApiResponse;
import com.example.core.pagination.CursorResponse;
import com.example.product.controller.api.query.ProductQueryApi;
import com.example.product.dto.goods.response.GoodsDetailResponse;
import com.example.product.service.query.ProductQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductQueryController implements ProductQueryApi {

    private final ProductQueryService productQueryService;

    @Override
    public ApiResponse<GoodsDetailResponse> findById(Long itemId) {
        return ApiResponse.success(productQueryService.findProductById(itemId));
    }

    @Override
    public ApiResponse<CursorResponse<GoodsDetailResponse>> findList(String cursor, int size) {
        return ApiResponse.success(productQueryService.findProductList(cursor, size));
    }
}
