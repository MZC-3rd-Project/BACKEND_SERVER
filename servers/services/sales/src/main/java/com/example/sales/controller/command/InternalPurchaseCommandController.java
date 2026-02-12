package com.example.sales.controller.command;

import com.example.api.response.ApiResponse;
import com.example.sales.service.command.PurchaseCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Internal", description = "내부 서비스 간 호출용 API")
@RestController
@RequestMapping("/internal/v1/sales/purchases")
@RequiredArgsConstructor
public class InternalPurchaseCommandController {

    private final PurchaseCommandService purchaseCommandService;

    @Operation(summary = "구매 취소 (내부)")
    @PostMapping("/{purchaseId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long purchaseId,
                                     @RequestParam Long userId) {
        purchaseCommandService.cancel(purchaseId, userId);
        return ApiResponse.success();
    }
}
