package com.example.search.service.reconciliation;

import com.example.search.client.StockQueryClient;
import com.example.search.dto.reconciliation.response.StockReconciliationResponse;
import com.example.search.service.metrics.SearchMetricsService;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockReconciliationServiceTest {

    @Mock
    private RestClient restClient;

    @Mock
    private StockQueryClient stockQueryClient;

    @Mock
    private SearchMetricsService searchMetricsService;

    private StockReconciliationService stockReconciliationService;

    @BeforeEach
    void setUp() {
        stockReconciliationService = new StockReconciliationService(
                restClient,
                stockQueryClient,
                searchMetricsService
        );
        ReflectionTestUtils.setField(stockReconciliationService, "runbookUrl", "https://runbook.example/search");
    }

    @Test
    void reconcileItem_returnsMatchedWhenStocksAreEqual() throws Exception {
        when(stockQueryClient.fetchAvailableStockTotal(101L)).thenReturn(12);

        Response response = org.mockito.Mockito.mock(Response.class);
        when(response.getEntity()).thenReturn(new StringEntity("""
                {
                  "found": true,
                  "_source": {
                    "itemId": 101,
                    "stock": 12
                  }
                }
                """, ContentType.APPLICATION_JSON));
        when(restClient.performRequest(any(Request.class))).thenReturn(response);

        StockReconciliationResponse result = stockReconciliationService.reconcileItem(101L);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.getResult()).isEqualTo("matched");
        assertThat(result.getSourceAvailableStock()).isEqualTo(12);
        assertThat(result.getIndexedStock()).isEqualTo(12);
        verify(searchMetricsService).recordStockReconciliation("matched");
    }

    @Test
    void reconcileItem_returnsMismatchWhenValuesDiffer() throws Exception {
        when(stockQueryClient.fetchAvailableStockTotal(102L)).thenReturn(10);

        Response response = org.mockito.Mockito.mock(Response.class);
        when(response.getEntity()).thenReturn(new StringEntity("""
                {
                  "found": true,
                  "_source": {
                    "itemId": 102,
                    "stock": 8
                  }
                }
                """, ContentType.APPLICATION_JSON));
        when(restClient.performRequest(any(Request.class))).thenReturn(response);

        StockReconciliationResponse result = stockReconciliationService.reconcileItem(102L);

        assertThat(result.isMatched()).isFalse();
        assertThat(result.getResult()).isEqualTo("mismatch");
        assertThat(result.getSourceAvailableStock()).isEqualTo(10);
        assertThat(result.getIndexedStock()).isEqualTo(8);
        verify(searchMetricsService).recordStockReconciliation("mismatch");
    }

    @Test
    void reconcileItem_returnsSourceErrorWhenStockQueryFails() {
        when(stockQueryClient.fetchAvailableStockTotal(103L))
                .thenThrow(new IllegalStateException("stock down"));

        StockReconciliationResponse result = stockReconciliationService.reconcileItem(103L);

        assertThat(result.isMatched()).isFalse();
        assertThat(result.getResult()).isEqualTo("source_error");
        assertThat(result.getSourceAvailableStock()).isNull();
        assertThat(result.getIndexedStock()).isNull();
        verify(searchMetricsService).recordStockReconciliation("source_error");
    }
}
