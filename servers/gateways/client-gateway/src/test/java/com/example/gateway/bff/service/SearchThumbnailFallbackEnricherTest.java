package com.example.gateway.bff.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchThumbnailFallbackEnricherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SearchThumbnailFallbackEnricher enricher = new SearchThumbnailFallbackEnricher(objectMapper);

    @Test
    void collectFallbackMediaIds_returnsOnlyMissingThumbnailTargets() throws Exception {
        JsonNode body = objectMapper.readTree("""
                {
                  "success": true,
                  "data": {
                    "items": [
                      {"itemId": 1, "thumbnailMediaId": 11, "thumbnailUrl": null},
                      {"itemId": 2, "thumbnailMediaId": 11, "thumbnailUrl": ""},
                      {"itemId": 3, "thumbnailMediaId": 12, "thumbnailUrl": "https://cdn/already.jpg"},
                      {"itemId": 4, "thumbnailMediaId": null, "thumbnailUrl": null},
                      {"itemId": 5}
                    ]
                  }
                }
                """);

        List<Long> fallbackMediaIds = enricher.collectFallbackMediaIds(body);

        assertThat(fallbackMediaIds).containsExactly(11L);
    }

    @Test
    void applyFallbackUrls_setsUrlWhenMissingOnly() throws Exception {
        JsonNode body = objectMapper.readTree("""
                {
                  "success": true,
                  "data": {
                    "items": [
                      {"itemId": 1, "thumbnailMediaId": 11, "thumbnailUrl": null},
                      {"itemId": 2, "thumbnailMediaId": 12, "thumbnailUrl": "https://cdn/keep.jpg"},
                      {"itemId": 3, "thumbnailMediaId": 13, "thumbnailUrl": ""}
                    ]
                  }
                }
                """);

        JsonNode enriched = enricher.applyFallbackUrls(body, Map.of(
                11L, "https://cdn/fallback-11.webp",
                13L, "https://cdn/fallback-13.webp"
        ));

        JsonNode items = enriched.path("data").path("items");
        assertThat(items.get(0).path("thumbnailUrl").asText()).isEqualTo("https://cdn/fallback-11.webp");
        assertThat(items.get(1).path("thumbnailUrl").asText()).isEqualTo("https://cdn/keep.jpg");
        assertThat(items.get(2).path("thumbnailUrl").asText()).isEqualTo("https://cdn/fallback-13.webp");
    }

    @Test
    void applyFallbackUrls_returnsOriginalWhenMapIsEmpty() throws Exception {
        JsonNode body = objectMapper.readTree("""
                {
                  "success": true,
                  "data": {
                    "items": [
                      {"itemId": 1, "thumbnailMediaId": 11, "thumbnailUrl": null}
                    ]
                  }
                }
                """);

        JsonNode enriched = enricher.applyFallbackUrls(body, Map.of());

        assertThat(enriched).isSameAs(body);
    }
}

