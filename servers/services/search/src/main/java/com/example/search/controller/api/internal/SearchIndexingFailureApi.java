package com.example.search.controller.api.internal;

import com.example.api.response.ApiResponse;
import com.example.search.dto.index.response.IndexingFailureRetryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Tag(name = "Search Internal DLT", description = "검색 인덱싱 실패 재처리 API")
public interface SearchIndexingFailureApi {

    @Operation(summary = "인덱싱 실패 건 재처리")
    @PostMapping("/indexing-failures/{failureId}/retry")
    ApiResponse<IndexingFailureRetryResponse> retryFailure(@PathVariable Long failureId);
}
