package com.example.search.controller.internal;

import com.example.api.response.ApiResponse;
import com.example.search.controller.api.internal.SearchIndexManagementApi;
import com.example.search.dto.index.response.IndexRecreateResponse;
import com.example.search.service.index.IndexManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/search/indexes")
@RequiredArgsConstructor
public class SearchIndexManagementController implements SearchIndexManagementApi {

    private final IndexManagementService indexManagementService;

    @Override
    public ApiResponse<IndexRecreateResponse> recreateIndex(String indexName) {
        return ApiResponse.success(indexManagementService.recreateItemsIndex(indexName));
    }
}
