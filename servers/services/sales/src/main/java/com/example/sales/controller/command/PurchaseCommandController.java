package com.example.sales.controller.command;

import com.example.api.response.ApiResponse;
import com.example.sales.controller.api.command.PurchaseCommandApi;
import com.example.sales.dto.request.PurchaseRequest;
import com.example.sales.dto.response.PurchaseResponse;
import com.example.sales.service.command.PurchaseCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales/purchase")
@RequiredArgsConstructor
public class PurchaseCommandController implements PurchaseCommandApi {

    private final PurchaseCommandService purchaseCommandService;

    @Override
    public ApiResponse<PurchaseResponse> purchase(PurchaseRequest request, Long userId) {
        return ApiResponse.success(purchaseCommandService.purchase(request, userId));
    }
}
