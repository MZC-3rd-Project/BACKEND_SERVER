package com.example.search.document;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchCategoryCodeResolverTest {

    @Test
    void resolve_collectsFundingCodeAndCategoryAliases() {
        List<String> resolved = SearchCategoryCodeResolver.resolve(
                "문구",
                List.of("굿즈", "캘린더"),
                "COLLECTIBLE"
        );

        assertThat(resolved).containsExactly("COLLECTIBLE", "DESK");
    }

    @Test
    void resolve_mapsKoreanAliasesToStableCodes() {
        List<String> resolved = SearchCategoryCodeResolver.resolve(
                "구장 굿즈",
                List.of("스포츠", "응원 굿즈"),
                null
        );

        assertThat(resolved).containsExactly("COLLECTIBLE");
    }

    @Test
    void resolve_promotesFundingChildCodeToCollectible() {
        List<String> resolved = SearchCategoryCodeResolver.resolve(
                null,
                List.of(),
                "DESK"
        );

        assertThat(resolved).containsExactly("DESK", "COLLECTIBLE");
    }
}
