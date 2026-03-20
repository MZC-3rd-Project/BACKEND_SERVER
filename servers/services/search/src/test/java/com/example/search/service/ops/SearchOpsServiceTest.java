package com.example.search.service.ops;

import com.example.search.client.ProductSearchSourceClient;
import com.example.search.client.StockSummaryClient;
import com.example.search.client.StoreSnapshotClient;
import com.example.search.client.dto.ProductSearchDocument;
import com.example.search.client.dto.SearchDocumentPage;
import com.example.search.client.dto.StockSummary;
import com.example.search.client.dto.StoreSnapshot;
import com.example.search.dto.request.SearchReindexRequest;
import com.example.search.dto.response.SearchReindexResponse;
import com.example.search.service.index.ElasticsearchDocumentClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchOpsServiceTest {

    private ProductSearchSourceClient productSearchSourceClient;
    private StockSummaryClient stockSummaryClient;
    private StoreSnapshotClient storeSnapshotClient;
    private ElasticsearchDocumentClient elasticsearchDocumentClient;
    private SearchOpsService searchOpsService;

    @BeforeEach
    void setUp() {
        productSearchSourceClient = mock(ProductSearchSourceClient.class);
        stockSummaryClient = mock(StockSummaryClient.class);
        storeSnapshotClient = mock(StoreSnapshotClient.class);
        elasticsearchDocumentClient = mock(ElasticsearchDocumentClient.class);
        searchOpsService = new SearchOpsService(
                productSearchSourceClient,
                stockSummaryClient,
                storeSnapshotClient,
                elasticsearchDocumentClient
        );
    }

    @Test
    void reindexItems_recreatesIndexAndProcessesMultiplePages() {
        ProductSearchDocument first = document(100L, 10L);
        ProductSearchDocument second = document(90L, 10L);
        ProductSearchDocument third = document(80L, 11L);

        when(productSearchSourceClient.findSearchDocuments(null, 2))
                .thenReturn(new SearchDocumentPage(List.of(first, second), "cursor-2"));
        when(productSearchSourceClient.findSearchDocuments("cursor-2", 2))
                .thenReturn(new SearchDocumentPage(List.of(third), null));
        when(stockSummaryClient.findByItemId(100L)).thenReturn(Optional.of(new StockSummary(100L, 17, 11, 0, 6, false)));
        when(stockSummaryClient.findByItemId(90L)).thenReturn(Optional.of(new StockSummary(90L, 17, 10, 0, 7, false)));
        when(stockSummaryClient.findByItemId(80L)).thenReturn(Optional.of(new StockSummary(80L, 17, 9, 0, 8, false)));
        when(storeSnapshotClient.findStore(10L))
                .thenReturn(Optional.of(new StoreSnapshot(10L, "스토어A", "ACTIVE")));
        when(storeSnapshotClient.findStore(11L))
                .thenReturn(Optional.of(new StoreSnapshot(11L, "스토어B", "ACTIVE")));

        SearchReindexResponse response = searchOpsService.reindexItems(
                new SearchReindexRequest(null, 2, 10, true)
        );

        assertThat(response.indexedCount()).isEqualTo(3L);
        assertThat(response.processedPages()).isEqualTo(2);
        assertThat(response.finished()).isTrue();
        assertThat(response.recreatedIndex()).isTrue();
        verify(elasticsearchDocumentClient).recreateIndex();
        verify(elasticsearchDocumentClient, times(3)).upsert(any());
        verify(storeSnapshotClient, times(1)).findStore(10L);
        verify(storeSnapshotClient, times(1)).findStore(11L);
    }

    @Test
    void reindexItems_stopsWhenMaxPagesReached() {
        ProductSearchDocument first = document(100L, 10L);

        when(productSearchSourceClient.findSearchDocuments(null, 1))
                .thenReturn(new SearchDocumentPage(List.of(first), "cursor-2"));
        when(stockSummaryClient.findByItemId(100L)).thenReturn(Optional.of(new StockSummary(100L, 17, 11, 0, 6, false)));
        when(storeSnapshotClient.findStore(10L))
                .thenReturn(Optional.of(new StoreSnapshot(10L, "스토어A", "ACTIVE")));

        SearchReindexResponse response = searchOpsService.reindexItems(
                new SearchReindexRequest(null, 1, 1, false)
        );

        assertThat(response.indexedCount()).isEqualTo(1L);
        assertThat(response.processedPages()).isEqualTo(1);
        assertThat(response.finished()).isFalse();
        assertThat(response.nextCursor()).isEqualTo("cursor-2");
        verify(elasticsearchDocumentClient, times(1)).upsert(any());
        verify(elasticsearchDocumentClient, times(0)).recreateIndex();
    }

    private ProductSearchDocument document(Long itemId, Long storeId) {
        return new ProductSearchDocument(
                itemId,
                "테스트 " + itemId,
                "설명",
                1L,
                "카테고리",
                List.of("카테고리"),
                "GOODS",
                "ON_SALE",
                10_000L,
                300L,
                storeId,
                400L,
                List.of("태그"),
                List.of("특징"),
                List.of("상세 제목"),
                List.of("상세 설명"),
                List.of("하이라이트"),
                17,
                LocalDateTime.of(2026, 3, 19, 10, 0),
                LocalDateTime.of(2026, 3, 19, 11, 0)
        );
    }
}
