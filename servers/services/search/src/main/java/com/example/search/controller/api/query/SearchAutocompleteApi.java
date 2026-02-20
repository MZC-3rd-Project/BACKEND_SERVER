package com.example.search.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.search.dto.autocomplete.response.AutocompleteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Search Autocomplete", description = "검색 자동완성 API")
public interface SearchAutocompleteApi {

    @Operation(summary = "검색어 자동완성")
    @GetMapping("/autocomplete")
    ApiResponse<AutocompleteResponse> autocomplete(
            @RequestParam @NotBlank String q,
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) int size
    );
}
