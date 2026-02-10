package com.example.product.controller.query;

import com.example.api.response.ApiResponse;
import com.example.core.pagination.CursorResponse;
import com.example.product.controller.api.query.GoodsQueryApi;
import com.example.product.dto.goods.response.GoodsDetailResponse;
import com.example.product.service.query.GoodsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/goods")
@RequiredArgsConstructor
public class GoodsQueryController implements GoodsQueryApi {

    private final GoodsQueryService goodsQueryService;

    @Override
    public ApiResponse<GoodsDetailResponse> findById(Long itemId) {
        return ApiResponse.success(goodsQueryService.findGoodsById(itemId));
    }

    @Override
    public ApiResponse<CursorResponse<GoodsDetailResponse>> findList(String cursor, int size) {
        return ApiResponse.success(goodsQueryService.findGoodsList(cursor, size));
    }
}
