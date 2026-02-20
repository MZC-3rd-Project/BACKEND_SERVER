package com.example.search.dto.search.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SearchResponse {

    private final List<SearchItemResponse> items;
    private final String nextCursor;
    private final Long total;
}
