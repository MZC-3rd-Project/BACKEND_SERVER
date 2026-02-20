package com.example.search.service.query;

import com.example.core.pagination.CursorResponse;
import com.example.search.dto.search.request.SearchRequest;
import com.example.search.dto.search.response.SearchItemResponse;
import com.example.search.service.query.autocomplete.AutocompleteService;
import com.example.search.service.query.cache.SearchResultCacheService;
import com.example.search.service.query.popular.PopularSearchService;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchQueryServiceTest {

    @Mock
    private RestClient restClient;

    @Mock
    private AutocompleteService autocompleteService;

    @Mock
    private PopularSearchService popularSearchService;

    @Mock
    private SearchResultCacheService searchResultCacheService;

    private SearchQueryService searchQueryService;

    @BeforeEach
    void setUp() {
        searchQueryService = new SearchQueryService(
                restClient,
                new SearchCursorCodec(),
                autocompleteService,
                popularSearchService,
                searchResultCacheService
        );
    }

    @Test
    void search_buildsQueryWithFiltersAndParsesResponse() throws Exception {
        String responseJson = """
                {
                  "hits": {
                    "total": {"value": 1, "relation": "eq"},
                    "hits": [
                      {
                        "_source": {
                          "itemId": 101,
                          "title": "아이폰 케이스",
                          "category": "GOODS",
                          "price": 3000,
                          "status": "SELLING",
                          "stock": 7
                        },
                        "_score": 1.23,
                        "sort": [3000, 101],
                        "highlight": {
                          "title": ["<em>아이폰</em> 케이스"]
                        }
                      }
                    ]
                  }
                }
                """;

        Response response = mock(Response.class);
        when(response.getEntity()).thenReturn(new StringEntity(responseJson, ContentType.APPLICATION_JSON));
        when(restClient.performRequest(any(Request.class))).thenReturn(response);
        when(searchResultCacheService.get(any())).thenReturn(java.util.Optional.empty());

        SearchRequest request = new SearchRequest();
        request.setQ("아이폰");
        request.setCategory("GOODS");
        request.setStatus(java.util.List.of("SELLING"));
        request.setMinPrice(1000L);
        request.setMaxPrice(5000L);
        request.setSort("PRICE_ASC");
        request.setSize(1);

        CursorResponse<SearchItemResponse> result = searchQueryService.search(request);

        assertThat(result.getTotalCount()).isEqualTo(1L);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getItemId()).isEqualTo(101L);
        assertThat(result.getItems().get(0).getHighlightedTitle()).contains("<em>");
        assertThat(result.getNextCursor()).isNotBlank();
        verify(searchResultCacheService).get(any(SearchRequest.class));
        verify(searchResultCacheService).put(any(SearchRequest.class), anyString());
        verify(autocompleteService).recordKeyword("아이폰");
        verify(popularSearchService).recordKeyword("아이폰");

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(restClient).performRequest(captor.capture());
        Request esRequest = captor.getValue();

        assertThat(esRequest.getMethod()).isEqualTo("POST");
        assertThat(esRequest.getEndpoint()).isEqualTo("/items/_search");

        String body = EntityUtils.toString(esRequest.getEntity());
        assertThat(body).contains("\"multi_match\"");
        assertThat(body).contains("\"category\"");
        assertThat(body).contains("\"range\"");
        assertThat(body).contains("\"highlight\"");
    }

    @Test
    void search_returnsCachedResultWhenCacheHit() throws Exception {
        String cachedJson = """
                {
                  "hits": {
                    "total": {"value": 1, "relation": "eq"},
                    "hits": [
                      {
                        "_source": {
                          "itemId": 101,
                          "title": "아이폰 케이스",
                          "category": "GOODS",
                          "price": 3000,
                          "status": "SELLING",
                          "stock": 7
                        },
                        "_score": 1.23,
                        "sort": [3000, 101]
                      }
                    ]
                  }
                }
                """;

        when(searchResultCacheService.get(any())).thenReturn(java.util.Optional.of(cachedJson));

        SearchRequest request = new SearchRequest();
        request.setQ("아이폰");
        request.setSize(1);

        CursorResponse<SearchItemResponse> result = searchQueryService.search(request);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getItemId()).isEqualTo(101L);
        verify(restClient, never()).performRequest(any(Request.class));
        verify(searchResultCacheService, never()).put(any(SearchRequest.class), anyString());
        verify(autocompleteService).recordKeyword("아이폰");
        verify(popularSearchService).recordKeyword("아이폰");
    }
}
