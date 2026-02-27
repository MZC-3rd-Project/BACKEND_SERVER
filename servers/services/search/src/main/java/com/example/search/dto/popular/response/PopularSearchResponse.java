package com.example.search.dto.popular.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PopularSearchResponse {

    private final List<KeywordCount> keywords;

    @Getter
    @Builder
    public static class KeywordCount {

        private final String keyword;
        private final long count;
    }
}
