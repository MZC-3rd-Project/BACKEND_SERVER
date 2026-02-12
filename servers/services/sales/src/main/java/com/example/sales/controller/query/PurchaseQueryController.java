package com.example.sales.controller.query;

import com.example.api.response.ApiResponse;
import com.example.core.pagination.CursorResponse;
import com.example.sales.controller.api.query.PurchaseQueryApi;
import com.example.sales.dto.response.PurchaseResponse;
import com.example.sales.service.query.PurchaseQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales/purchases")
@RequiredArgsConstructor
public class PurchaseQueryController implements PurchaseQueryApi {

    private final PurchaseQueryService purchaseQueryService;

    @Override
    public ApiResponse<CursorResponse<PurchaseResponse>> findMyPurchases(Long userId, String cursor, int size) {
        return ApiResponse.success(purchaseQueryService.findByUserId(userId, cursor, size));
    }

    @Override
    public ApiResponse<PurchaseResponse> findPurchaseDetail(Long purchaseId, Long userId) {
        return ApiResponse.success(purchaseQueryService.findById(purchaseId, userId));
    }
}
