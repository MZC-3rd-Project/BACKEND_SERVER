package com.example.product.controller.query;

import com.example.api.response.ApiResponse;
import com.example.product.dto.item.request.ItemQuoteRequest;
import com.example.product.dto.item.response.ItemQuoteResponse;
import com.example.product.service.query.InternalItemQuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Internal", description = "내부 서비스 간 호출용 API")
@RestController
@RequestMapping("/internal/v1/items")
@RequiredArgsConstructor
public class InternalItemQuoteController {

    private final InternalItemQuoteService internalItemQuoteService;

    @Operation(summary = "상품 다건 견적 조회 (내부)")
    @PostMapping("/quote")
    public ApiResponse<ItemQuoteResponse> quote(@Valid @RequestBody ItemQuoteRequest request) {
        return ApiResponse.success(internalItemQuoteService.quote(request));
    }
}
