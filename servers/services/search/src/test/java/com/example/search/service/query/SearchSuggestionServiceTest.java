package com.example.search.service.query;

import com.example.search.dto.response.SearchSuggestionResponse;
import com.example.search.service.index.ElasticsearchDocumentClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchSuggestionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ElasticsearchDocumentClient elasticsearchDocumentClient;
    private SearchSuggestionService searchSuggestionService;

    @BeforeEach
    void setUp() {
        elasticsearchDocumentClient = mock(ElasticsearchDocumentClient.class);
        searchSuggestionService = new SearchSuggestionService(objectMapper, elasticsearchDocumentClient);
    }

    @Test
    void suggest_returnsMixedSuggestionsFromSearchHits() throws Exception {
        when(elasticsearchDocumentClient.search(any())).thenReturn(objectMapper.readTree("""
                {
                  "hits": {
                    "hits": [
                      {
                        "_source": {
                          "title": "구장 관련 상품 목록",
                          "tags": ["구장", "응원 굿즈"],
                          "storeName": "구장 굿즈 스토어",
                          "category": "굿즈",
                          "categoryPath": ["스포츠", "구장 굿즈"]
                        }
                      },
                      {
                        "_source": {
                          "title": "구장 데스크 캘린더",
                          "tags": ["캘린더", "한정판"],
                          "storeName": "캘린더 스토어",
                          "category": "문구",
                          "categoryPath": ["굿즈", "캘린더"]
                        }
                      }
                    ]
                  }
                }
                """));

        List<SearchSuggestionResponse> response = searchSuggestionService.suggest("구장", 5);

        assertThat(response).containsExactly(
                new SearchSuggestionResponse("구장 관련 상품 목록", "TITLE"),
                new SearchSuggestionResponse("구장", "TAG"),
                new SearchSuggestionResponse("구장 굿즈 스토어", "STORE"),
                new SearchSuggestionResponse("구장 굿즈", "CATEGORY"),
                new SearchSuggestionResponse("구장 데스크 캘린더", "TITLE")
        );

        ArgumentCaptor<ObjectNode> requestCaptor = ArgumentCaptor.forClass(ObjectNode.class);
        verify(elasticsearchDocumentClient).search(requestCaptor.capture());
        JsonNode queryNode = requestCaptor.getValue().path("query").path("bool").path("should");
        assertThat(queryNode.isArray()).isTrue();
        assertThat(queryNode).hasSize(5);
        assertThat(requestCaptor.getValue().path("_source").isArray()).isTrue();
    }

    @Test
    void suggest_returnsEmptyWhenQueryBlank() {
        assertThat(searchSuggestionService.suggest(" ", 10)).isEmpty();
    }
}
