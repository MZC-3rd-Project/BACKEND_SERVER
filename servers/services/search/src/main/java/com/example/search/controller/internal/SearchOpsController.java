package com.example.search.controller.internal;

import com.example.api.response.ApiResponse;
import com.example.search.dto.request.SearchItemEnrichmentPatchRequest;
import com.example.search.dto.request.SearchReindexRequest;
import com.example.search.dto.response.SearchItemEnrichmentPatchResponse;
import com.example.search.dto.response.SearchReindexResponse;
import com.example.search.service.enrichment.SearchEnrichmentService;
import com.example.search.service.ops.SearchOpsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Search Internal Ops", description = "검색 인덱스 운영용 내부 API")
@RestController
@RequestMapping("/internal/v1/search")
@RequiredArgsConstructor
public class SearchOpsController {

    private final SearchOpsService searchOpsService;
    private final SearchEnrichmentService searchEnrichmentService;

    @Operation(summary = "상품 전체 재색인")
    @PostMapping("/tasks/reindex-items")
    public ApiResponse<SearchReindexResponse> reindexItems(
            @Valid @RequestBody(required = false) SearchReindexRequest request
    ) {
        return ApiResponse.success(searchOpsService.reindexItems(request));
    }

    @Operation(summary = "상품 검색 문서 AI 보강 patch")
    @PutMapping("/items/{itemId}/enrichment")
    public ApiResponse<SearchItemEnrichmentPatchResponse> patchItemEnrichment(
            @PathVariable Long itemId,
            @Valid @RequestBody SearchItemEnrichmentPatchRequest request
    ) {
        return ApiResponse.success(searchEnrichmentService.applyItemEnrichment(itemId, request));
    }
}
