package com.example.search.controller.query;

import com.example.api.response.ApiResponse;
import com.example.search.controller.api.query.SearchApi;
import com.example.search.dto.search.request.SearchRequest;
import com.example.search.dto.search.response.SearchResponse;
import com.example.search.service.query.SearchQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchQueryController implements SearchApi {

    private final SearchQueryService searchQueryService;

    @Override
    public ApiResponse<SearchResponse> search(@Valid @ModelAttribute SearchRequest request) {
        return ApiResponse.success(searchQueryService.search(request));
    }
}
