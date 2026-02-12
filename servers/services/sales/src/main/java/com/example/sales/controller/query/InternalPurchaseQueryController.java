package com.example.sales.controller.query;

import com.example.api.response.ApiResponse;
import com.example.sales.dto.response.PurchaseResponse;
import com.example.sales.service.query.InternalPurchaseQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Internal", description = "내부 서비스 간 호출용 API")
@RestController
@RequestMapping("/internal/v1/sales/purchases")
@RequiredArgsConstructor
public class InternalPurchaseQueryController {

    private final InternalPurchaseQueryService internalPurchaseQueryService;

    @Operation(summary = "구매 단건 조회 (내부)")
    @GetMapping("/{purchaseId}")
    public ApiResponse<PurchaseResponse> findById(@PathVariable Long purchaseId) {
        return ApiResponse.success(internalPurchaseQueryService.findById(purchaseId));
    }

    @Operation(summary = "주문 ID 기반 구매 조회 (내부)")
    @GetMapping(params = "orderId")
    public ApiResponse<PurchaseResponse> findByOrderId(@RequestParam Long orderId) {
        return ApiResponse.success(internalPurchaseQueryService.findByOrderId(orderId));
    }
}
