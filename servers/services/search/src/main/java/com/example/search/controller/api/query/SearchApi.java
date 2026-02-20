package com.example.search.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.core.pagination.CursorResponse;
import com.example.search.dto.search.response.SearchItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "Search Query", description = "검색 조회 API")
public interface SearchApi {

    @Operation(summary = "상품 검색")
    @GetMapping
    ApiResponse<CursorResponse<SearchItemResponse>> search(
            @RequestParam String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false, name = "status") List<String> status,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(defaultValue = "LATEST") String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    );
}
