package com.example.search.service.query;

import com.example.search.dto.search.request.SearchRequest;
import com.example.search.dto.search.response.SearchResponse;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchQueryServiceTest {

    @Mock
    private RestClient restClient;

    private SearchQueryService searchQueryService;

    @BeforeEach
    void setUp() {
        searchQueryService = new SearchQueryService(restClient, new SearchCursorCodec());
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

        SearchRequest request = new SearchRequest();
        request.setQ("아이폰");
        request.setCategory("GOODS");
        request.setStatus(java.util.List.of("SELLING"));
        request.setMinPrice(1000L);
        request.setMaxPrice(5000L);
        request.setSort("PRICE_ASC");
        request.setSize(1);

        SearchResponse result = searchQueryService.search(request);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getItemId()).isEqualTo(101L);
        assertThat(result.getItems().get(0).getHighlightedTitle()).contains("<em>");
        assertThat(result.getNextCursor()).isNotBlank();

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
}
