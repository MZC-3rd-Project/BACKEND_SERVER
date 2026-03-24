package com.example.search.service.query;

import com.example.search.service.index.ElasticsearchDocumentClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchQueryServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ElasticsearchDocumentClient elasticsearchDocumentClient;
    private SearchQueryService searchQueryService;

    @BeforeEach
    void setUp() {
        elasticsearchDocumentClient = mock(ElasticsearchDocumentClient.class);
        searchQueryService = new SearchQueryService(objectMapper, elasticsearchDocumentClient);
    }

    @Test
    void search_returnsGatewayCompatibleItemsAndCursor() throws Exception {
        when(elasticsearchDocumentClient.search(any())).thenReturn(objectMapper.readTree("""
                {
                  "hits": {
                    "total": { "value": 3 },
                    "hits": [
                      {
                        "_source": {
                          "itemId": 101,
                          "title": "런닝화",
                          "domainType": "PRODUCT",
                          "status": "ON_SALE",
                          "salesChannel": "NORMAL",
                          "price": 32000,
                          "basePrice": 32000,
                          "effectivePrice": 29000,
                          "stock": 20,
                          "availableStock": 12,
                          "thumbnailMediaId": 501
                        }
                      },
                      {
                        "_source": {
                          "itemId": 102,
                          "title": "굿즈 세트",
                          "domainType": "GOODS",
                          "status": "HOT_DEAL",
                          "salesChannel": "HOT_DEAL",
                          "price": 18000,
                          "basePrice": 18000,
                          "effectivePrice": 15000,
                          "activeHotDealId": 9001,
                          "thumbnailMediaId": 502
                        }
                      }
                    ]
                  }
                }
                """));

        SearchQuery query = SearchQuery.of(
                "런닝",
                null,
                "PRODUCT",
                null,
                null,
                null,
                "LATEST",
                null,
                2
        );

        var response = searchQueryService.search(query);

        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getTotalCount()).isEqualTo(3);
        assertThat(response.getNextCursor()).isNotBlank();
        assertThat(response.getItems().get(0).itemId()).isEqualTo(101L);
        assertThat(response.getItems().get(0).salesChannel()).isEqualTo("NORMAL");
        assertThat(response.getItems().get(0).stock()).isEqualTo(20);
        assertThat(response.getItems().get(0).availableStock()).isEqualTo(12);
        assertThat(response.getItems().get(1).activeHotDealId()).isEqualTo(9001L);

        ArgumentCaptor<ObjectNode> requestCaptor = ArgumentCaptor.forClass(ObjectNode.class);
        verify(elasticsearchDocumentClient).search(requestCaptor.capture());
        JsonNode fields = requestCaptor.getValue()
                .path("query")
                .path("bool")
                .path("must")
                .path(0)
                .path("bool")
                .path("should")
                .path(0)
                .path("multi_match")
                .path("fields");
        assertThat(fields.isArray()).isTrue();
        List<String> fieldNames = new ArrayList<>();
        fields.forEach(node -> fieldNames.add(node.asText()));
        assertThat(fieldNames).contains("aiTags^3", "aiKeywords^2", "aiSummary^1.5");
        assertThat(requestCaptor.getValue()
                .path("query")
                .path("bool")
                .path("must")
                .path(0)
                .path("bool")
                .path("should")
                .toString()).contains("categoryCodes");
    }

    @Test
    void search_returnsNoNextCursorAtLastPage() throws Exception {
        when(elasticsearchDocumentClient.search(any())).thenReturn(objectMapper.readTree("""
                {
                  "hits": {
                    "total": { "value": 1 },
                    "hits": [
                      {
                        "_source": {
                          "itemId": 201,
                          "title": "공연 티켓",
                          "domainType": "PERFORMANCE",
                          "status": "FUNDING",
                          "salesChannel": "FUNDING",
                          "price": 55000
                        }
                      }
                    ]
                  }
                }
                """));

        SearchQuery query = SearchQuery.of(
                null,
                null,
                null,
                null,
                null,
                null,
                "LATEST",
                null,
                20
        );

        var response = searchQueryService.search(query);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getNextCursor()).isNull();
        assertThat(response.getTotalCount()).isEqualTo(1L);
    }

    @Test
    void search_supportsCategoryCodeInKeywordAndFilter() throws Exception {
        when(elasticsearchDocumentClient.search(any())).thenReturn(objectMapper.readTree("""
                {
                  "hits": {
                    "total": { "value": 0 },
                    "hits": []
                  }
                }
                """));

        SearchQuery query = SearchQuery.of(
                "COLLECTIBLE",
                "COLLECTIBLE",
                null,
                null,
                null,
                null,
                "LATEST",
                null,
                12
        );

        searchQueryService.search(query);

        ArgumentCaptor<ObjectNode> requestCaptor = ArgumentCaptor.forClass(ObjectNode.class);
        verify(elasticsearchDocumentClient).search(requestCaptor.capture());
        String requestJson = requestCaptor.getValue().toString();
        assertThat(requestJson).contains("\"categoryCodes\":\"COLLECTIBLE\"");
    }
}
