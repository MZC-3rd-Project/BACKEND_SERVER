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

        indexingService.indexItem(10L, "테스트 상품", "GOODS", "GOODS", 10000L, "SELLING", 20, 501L, 1234L);

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(restClient).performRequest(captor.capture());
        Request request = captor.getValue();

        assertThat(request.getMethod()).isEqualTo("PUT");
        assertThat(request.getEndpoint()).isEqualTo("/items-write/_doc/10");
        String json = EntityUtils.toString(request.getEntity());
        assertThat(json).contains("\"itemId\":10");
        assertThat(json).contains("\"title\":\"테스트 상품\"");
        assertThat(json).contains("\"category\":\"GOODS\"");
        assertThat(json).contains("\"domainType\":\"GOODS\"");
        assertThat(json).contains("\"stock\":20");
        assertThat(json).contains("\"thumbnailMediaId\":501");
        assertThat(json).contains("\"mediaVersion\":1234");
    }

    @Test
    void updateItem_sendsScriptedUpsertRequest() throws Exception {
        when(restClient.performRequest(any(Request.class))).thenReturn(mock(Response.class));

        indexingService.updateItem(15L, "업데이트 상품", 14000L, 999L, 222L);

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(restClient).performRequest(captor.capture());
        Request request = captor.getValue();

        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getEndpoint()).isEqualTo("/items-write/_update/15");
        String json = EntityUtils.toString(request.getEntity());
        assertThat(json).contains("\"scripted_upsert\":true");
        assertThat(json).contains("\"thumbnailMediaId\":999");
        assertThat(json).contains("\"mediaVersion\":222");
    }

    @Test
    void updateItemStatus_sendsUpdateRequestWithUpsert() throws Exception {
        when(restClient.performRequest(any(Request.class))).thenReturn(mock(Response.class));

        indexingService.updateItemStatus(15L, "SOLD_OUT");

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(restClient).performRequest(captor.capture());
        Request request = captor.getValue();

        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getEndpoint()).isEqualTo("/items-write/_update/15");
        String json = EntityUtils.toString(request.getEntity());
        assertThat(json).contains("\"scripted_upsert\":true");
        assertThat(json).contains("\"script\"");
        assertThat(json).contains("\"status\":\"SOLD_OUT\"");
    }

    @Test
    void applyHotDealStarted_sendsScriptedProjectionUpdate() throws Exception {
        when(restClient.performRequest(any(Request.class))).thenReturn(mock(Response.class));

        indexingService.applyHotDealStarted(15L, 9001L, 9900L);

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(restClient).performRequest(captor.capture());
        Request request = captor.getValue();

        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getEndpoint()).isEqualTo("/items-write/_update/15");
        String json = EntityUtils.toString(request.getEntity());
        assertThat(json).contains("\"scripted_upsert\":true");
        assertThat(json).contains("\"activeHotDealId\":9001");
        assertThat(json).contains("\"effectivePrice\":9900");
        assertThat(json).contains("\"salesChannel\":\"HOT_DEAL\"");
    }

    @Test
    void applyFundingClosed_sendsScriptedProjectionUpdate() throws Exception {
        when(restClient.performRequest(any(Request.class))).thenReturn(mock(Response.class));

        indexingService.applyFundingClosed(15L, 8001L, "FUNDED");

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(restClient).performRequest(captor.capture());
        Request request = captor.getValue();

        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getEndpoint()).isEqualTo("/items-write/_update/15");
        String json = EntityUtils.toString(request.getEntity());
        assertThat(json).contains("\"scripted_upsert\":true");
        assertThat(json).contains("\"campaignId\":8001");
        assertThat(json).contains("\"terminalStatus\":\"FUNDED\"");
    }

    @Test
    void updateItemStockVersioned_sendsScriptUpdateRequest() throws Exception {
        when(restClient.performRequest(any(Request.class))).thenReturn(mock(Response.class));

        indexingService.updateItemStockVersioned(15L, 9, 57L);

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(restClient).performRequest(captor.capture());
        Request request = captor.getValue();

        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getEndpoint()).isEqualTo("/items-write/_update/15");
        String json = EntityUtils.toString(request.getEntity());
        assertThat(json).contains("\"script\"");
        assertThat(json).contains("\"stockVersion\":57");
        assertThat(json).contains("\"stock\":9");
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

    @Test
    void deleteItem_sendsDeleteDocRequest() throws Exception {
        when(restClient.performRequest(any(Request.class))).thenReturn(mock(Response.class));

        indexingService.deleteItem(23L);

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(restClient).performRequest(captor.capture());
        Request request = captor.getValue();

        assertThat(request.getMethod()).isEqualTo("DELETE");
        assertThat(request.getEndpoint()).isEqualTo("/items-write/_doc/23");
    }
}
