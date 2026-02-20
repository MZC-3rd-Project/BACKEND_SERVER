package com.example.search.service.index;

import com.example.core.exception.BusinessException;
import com.example.search.exception.SearchErrorCode;
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

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElasticsearchIndexingServiceTest {

    @Mock
    private RestClient restClient;

    private ElasticsearchIndexingService indexingService;

    @BeforeEach
    void setUp() {
        indexingService = new ElasticsearchIndexingService(restClient);
    }

    @Test
    void indexItem_sendsPutDocRequest() throws Exception {
        when(restClient.performRequest(any(Request.class))).thenReturn(mock(Response.class));

        indexingService.indexItem(10L, "테스트 상품", "GOODS", 10000L, "SELLING", 20);

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(restClient).performRequest(captor.capture());
        Request request = captor.getValue();

        assertThat(request.getMethod()).isEqualTo("PUT");
        assertThat(request.getEndpoint()).isEqualTo("/items/_doc/10");
        String json = EntityUtils.toString(request.getEntity());
        assertThat(json).contains("\"itemId\":10");
        assertThat(json).contains("\"title\":\"테스트 상품\"");
        assertThat(json).contains("\"category\":\"GOODS\"");
        assertThat(json).contains("\"stock\":20");
    }

    @Test
    void updateItemStatus_sendsUpdateRequestWithUpsert() throws Exception {
        when(restClient.performRequest(any(Request.class))).thenReturn(mock(Response.class));

        indexingService.updateItemStatus(15L, "SOLD_OUT");

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(restClient).performRequest(captor.capture());
        Request request = captor.getValue();

        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getEndpoint()).isEqualTo("/items/_update/15");
        String json = EntityUtils.toString(request.getEntity());
        assertThat(json).contains("\"doc_as_upsert\":true");
        assertThat(json).contains("\"status\":\"SOLD_OUT\"");
    }

    @Test
    void updateItemStock_wrapsIOExceptionAsBusinessException() throws Exception {
        when(restClient.performRequest(any(Request.class))).thenThrow(new IOException("es-down"));

        assertThatThrownBy(() -> indexingService.updateItemStock(99L, 0))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(SearchErrorCode.SEARCH_INDEXING_FAILED);
                });
    }
}
