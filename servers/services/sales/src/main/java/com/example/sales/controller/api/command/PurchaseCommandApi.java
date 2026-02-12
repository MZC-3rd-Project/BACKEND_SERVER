package com.example.sales.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.sales.dto.request.PurchaseRequest;
import com.example.sales.dto.response.PurchaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Purchase Command", description = "일반 구매 API (쓰기)")
public interface PurchaseCommandApi {

    @Operation(summary = "일반 구매")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "구매 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상품 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "재고 부족")
    })
    @PostMapping
    ApiResponse<PurchaseResponse> purchase(
            @Valid @RequestBody PurchaseRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId);
}
