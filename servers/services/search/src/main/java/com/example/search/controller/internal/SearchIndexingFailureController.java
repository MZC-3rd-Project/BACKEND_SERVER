package com.example.search.controller.internal;

import com.example.api.response.ApiResponse;
import com.example.search.controller.api.internal.SearchIndexingFailureApi;
import com.example.search.dto.index.response.IndexingFailureRetryResponse;
import com.example.search.service.index.SearchIndexingFailureService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/search")
@RequiredArgsConstructor
public class SearchIndexingFailureController implements SearchIndexingFailureApi {

    private final SearchIndexingFailureService searchIndexingFailureService;

    @Override
    public ApiResponse<IndexingFailureRetryResponse> retryFailure(Long failureId) {
        return ApiResponse.success(searchIndexingFailureService.retryFailure(failureId));
    }
}
