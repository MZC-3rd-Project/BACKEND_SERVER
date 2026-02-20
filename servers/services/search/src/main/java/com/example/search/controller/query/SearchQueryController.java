package com.example.search.controller.query;

import com.example.api.response.ApiResponse;
import com.example.core.pagination.CursorResponse;
import com.example.search.controller.api.query.SearchApi;
import com.example.search.dto.search.request.SearchRequest;
import com.example.search.dto.search.response.SearchItemResponse;
import com.example.search.service.query.SearchQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchQueryController implements SearchApi {

    private final SearchQueryService searchQueryService;

    @Override
    public ApiResponse<CursorResponse<SearchItemResponse>> search(
            String q,
            String category,
            String domainType,
            List<String> status,
            Long minPrice,
            Long maxPrice,
            String sort,
            String cursor,
            int size
    ) {
        SearchRequest request = new SearchRequest();
        request.setQ(q);
        request.setCategory(category);
        request.setDomainType(domainType);
        request.setStatus(status);
        request.setMinPrice(minPrice);
        request.setMaxPrice(maxPrice);
        request.setSort(sort);
        request.setCursor(cursor);
        request.setSize(size);

        return ApiResponse.success(searchQueryService.search(request));
    }
}
