package com.example.product.controller.query;

import com.example.api.response.ApiResponse;
import com.example.product.dto.item.response.ItemSummaryResponse;
import com.example.product.service.query.InternalItemQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Internal", description = "내부 서비스 간 호출용 API")
@RestController
@RequestMapping("/internal/v1/items")
@RequiredArgsConstructor
public class InternalItemQueryController {

    private final InternalItemQueryService internalItemQueryService;

    @Operation(summary = "상품 단건 조회 (내부)")
    @GetMapping("/{itemId}")
    public ApiResponse<ItemSummaryResponse> findById(@PathVariable Long itemId) {
        return ApiResponse.success(internalItemQueryService.findById(itemId));
    }

    @Operation(summary = "상품 다건 조회 (내부)")
    @PostMapping("/batch")
    public ApiResponse<List<ItemSummaryResponse>> findByIds(@RequestBody List<Long> itemIds) {
        return ApiResponse.success(internalItemQueryService.findByIds(itemIds));
    }
}
