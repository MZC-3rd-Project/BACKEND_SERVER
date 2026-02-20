package com.example.search.controller.internal;

import com.example.api.response.ApiResponse;
import com.example.search.controller.api.internal.SearchPopularKeywordAdminApi;
import com.example.search.service.query.popular.PopularSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/internal/v1/search")
@RequiredArgsConstructor
public class SearchPopularKeywordAdminController implements SearchPopularKeywordAdminApi {

    private final PopularSearchService popularSearchService;

    @Override
    public ApiResponse<List<String>> blockedKeywords() {
        return ApiResponse.success(popularSearchService.getBlockedKeywords());
    }

    @Override
    public ApiResponse<List<String>> addBlockedKeyword(String keyword) {
        return ApiResponse.success(popularSearchService.addBlockedKeyword(keyword));
    }

    @Override
    public ApiResponse<List<String>> removeBlockedKeyword(String keyword) {
        return ApiResponse.success(popularSearchService.removeBlockedKeyword(keyword));
    }
}
