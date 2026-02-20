package com.example.search.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.search.dto.popular.response.PopularSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;

@Tag(name = "Search Popular", description = "인기 검색어 API")
public interface SearchPopularApi {

    @Operation(summary = "인기 검색어 TOP 10 조회")
    @GetMapping("/popular")
    ApiResponse<PopularSearchResponse> popular();
}
