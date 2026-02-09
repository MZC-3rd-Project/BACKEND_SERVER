package com.example.product.controller;

import com.example.api.response.ApiResponse;
import com.example.product.domain.item.Item;
import com.example.product.domain.item.ItemRepository;
import com.example.product.dto.item.ItemSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Internal", description = "내부 서비스 간 호출용 API")
@RestController
@RequestMapping("/internal/v1/items")
@RequiredArgsConstructor
public class InternalItemController {

    private final ItemRepository itemRepository;

    @Operation(summary = "상품 단건 조회 (내부)")
    @GetMapping("/{itemId}")
    public ApiResponse<ItemSummaryResponse> findById(@PathVariable Long itemId) {
        Item item = itemRepository.findById(itemId).orElse(null);
        if (item == null) return ApiResponse.success(null);
        return ApiResponse.success(ItemSummaryResponse.from(item));
    }

    @Operation(summary = "상품 다건 조회 (내부)", description = "ID 목록으로 상품 정보 일괄 조회")
    @PostMapping("/batch")
    public ApiResponse<List<ItemSummaryResponse>> findByIds(@RequestBody List<Long> itemIds) {
        List<Item> items = itemRepository.findAllById(itemIds);
        return ApiResponse.success(items.stream().map(ItemSummaryResponse::from).toList());
    }
}
