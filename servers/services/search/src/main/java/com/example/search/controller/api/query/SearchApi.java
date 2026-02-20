package com.example.search.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.search.dto.search.request.SearchRequest;
import com.example.search.dto.search.response.SearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;

@Tag(name = "Search Query", description = "검색 조회 API")
public interface SearchApi {

    @Operation(summary = "상품 검색")
    @GetMapping
    ApiResponse<SearchResponse> search(@Valid SearchRequest request);
}
