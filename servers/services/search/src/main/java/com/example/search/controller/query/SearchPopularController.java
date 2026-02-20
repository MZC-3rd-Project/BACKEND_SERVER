package com.example.search.controller.query;

import com.example.api.response.ApiResponse;
import com.example.search.controller.api.query.SearchPopularApi;
import com.example.search.dto.popular.response.PopularSearchResponse;
import com.example.search.service.query.popular.PopularSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchPopularController implements SearchPopularApi {

    private final PopularSearchService popularSearchService;

    @Override
    public ApiResponse<PopularSearchResponse> popular() {
        return ApiResponse.success(popularSearchService.getTopKeywords());
    }
}
