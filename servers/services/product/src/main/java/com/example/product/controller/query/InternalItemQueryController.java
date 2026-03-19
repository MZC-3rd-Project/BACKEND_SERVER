package com.example.product.controller.query;

import com.example.api.response.ApiResponse;
import com.example.core.pagination.CursorResponse;
import com.example.product.dto.item.response.InternalStoreItemSummaryResponse;
import com.example.product.dto.item.response.ItemSearchDocumentResponse;
import com.example.product.dto.item.response.ItemSummaryResponse;
import com.example.product.service.query.InternalItemQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

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

    @Operation(summary = "마감 임박 상품 조회 (내부)")
    @GetMapping("/ending-soon")
    public ApiResponse<List<ItemSummaryResponse>> findItemsEndingSoon() {
        return ApiResponse.success(internalItemQueryService.findItemsEndingSoon());
    }

    @Operation(summary = "스토어 기준 상품 요약 목록 조회 (내부)")
    @GetMapping("/stores/{storeId}/summaries")
    public ApiResponse<List<InternalStoreItemSummaryResponse>> findByStoreId(@PathVariable Long storeId) {
        return ApiResponse.success(internalItemQueryService.findByStoreId(storeId));
    }

    @Operation(summary = "상품 검색 문서 조회 (내부)")
    @GetMapping("/{itemId}/search-document")
    public ApiResponse<ItemSearchDocumentResponse> findSearchDocument(@PathVariable Long itemId) {
        return ApiResponse.success(internalItemQueryService.findSearchDocument(itemId));
    }

    @Operation(summary = "상품 검색 문서 다건 조회 (내부)")
    @PostMapping("/search-documents/batch")
    public ApiResponse<List<ItemSearchDocumentResponse>> findSearchDocuments(@RequestBody List<Long> itemIds) {
        return ApiResponse.success(internalItemQueryService.findSearchDocuments(itemIds));
    }

    @Operation(summary = "상품 검색 문서 페이지 조회 (내부)")
    @GetMapping("/search-documents")
    public ApiResponse<CursorResponse<ItemSearchDocumentResponse>> findSearchDocuments(
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "size", required = false, defaultValue = "100") Integer size
    ) {
        return ApiResponse.success(internalItemQueryService.findSearchDocuments(cursor, size));
    }
}
