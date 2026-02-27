package com.example.search.controller.api.internal;

import com.example.api.response.ApiResponse;
import com.example.search.dto.index.response.IndexRecreateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Search Internal Index", description = "검색 인덱스 내부 운영 API")
public interface SearchIndexManagementApi {

    @Operation(summary = "검색 인덱스 재생성")
    @PostMapping("/recreate")
    ApiResponse<IndexRecreateResponse> recreateIndex(
            @RequestParam(defaultValue = "items") String indexName
    );
}
