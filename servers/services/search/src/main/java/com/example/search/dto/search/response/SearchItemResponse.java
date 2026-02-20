package com.example.search.dto.search.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SearchItemResponse {

    private final Long itemId;
    private final String title;
    private final String category;
    private final String domainType;
    private final Long price;
    private final String status;
    private final Integer stock;

    private final Double score;
    private final String highlightedTitle;
    private final String highlightedDescription;
}
