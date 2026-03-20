package com.example.search.service.ops;

import com.example.search.client.ProductSearchSourceClient;
import com.example.search.client.StockSummaryClient;
import com.example.search.client.StoreSnapshotClient;
import com.example.search.client.dto.ProductSearchDocument;
import com.example.search.client.dto.SearchDocumentPage;
import com.example.search.client.dto.StockSummary;
import com.example.search.client.dto.StoreSnapshot;
import com.example.search.document.ItemDocument;
import com.example.search.dto.request.SearchReindexRequest;
import com.example.search.dto.response.SearchReindexResponse;
import com.example.search.service.index.ElasticsearchDocumentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchOpsService {

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int MAX_BATCH_SIZE = 500;
    private static final int DEFAULT_MAX_PAGES = 20;
    private static final int MAX_MAX_PAGES = 1_000;

    private final ProductSearchSourceClient productSearchSourceClient;
    private final StockSummaryClient stockSummaryClient;
    private final StoreSnapshotClient storeSnapshotClient;
    private final ElasticsearchDocumentClient elasticsearchDocumentClient;

    @Transactional(readOnly = true)
    public SearchReindexResponse reindexItems(SearchReindexRequest request) {
        SearchReindexRequest safeRequest = request == null
                ? new SearchReindexRequest(null, null, null, null)
                : request;

        String requestedCursor = trimToNull(safeRequest.cursor());
        int batchSize = normalizeSize(safeRequest.size());
        int maxPages = normalizeMaxPages(safeRequest.maxPages());

        if (safeRequest.shouldRecreateIndex()) {
            elasticsearchDocumentClient.recreateIndex();
        }

        int processedPages = 0;
        long indexedCount = 0L;
        String nextCursor = requestedCursor;
        Map<Long, Optional<StoreSnapshot>> storeSnapshotCache = new HashMap<>();

        while (processedPages < maxPages) {
            SearchDocumentPage page = productSearchSourceClient.findSearchDocuments(nextCursor, batchSize);
            if (page.items().isEmpty()) {
                nextCursor = null;
                break;
            }

            for (ProductSearchDocument document : page.items()) {
                StoreSnapshot storeSnapshot = resolveStore(storeSnapshotCache, document.storeId());
                Integer availableStock = resolveAvailableStock(document.itemId());
                elasticsearchDocumentClient.upsert(ItemDocument.from(document, storeSnapshot, availableStock));
                indexedCount++;
            }

            processedPages++;
            nextCursor = trimToNull(page.nextCursor());
            if (!StringUtils.hasText(nextCursor)) {
                break;
            }
        }

        boolean finished = !StringUtils.hasText(nextCursor);
        log.info(
                "[SearchOps] item reindex finished. requestedCursor={}, nextCursor={}, batchSize={}, maxPages={}, processedPages={}, indexedCount={}, recreatedIndex={}, finished={}",
                requestedCursor,
                nextCursor,
                batchSize,
                maxPages,
                processedPages,
                indexedCount,
                safeRequest.shouldRecreateIndex(),
                finished
        );

        return SearchReindexResponse.builder()
                .requestedCursor(requestedCursor)
                .nextCursor(nextCursor)
                .batchSize(batchSize)
                .maxPages(maxPages)
                .processedPages(processedPages)
                .indexedCount(indexedCount)
                .recreatedIndex(safeRequest.shouldRecreateIndex())
                .finished(finished)
                .build();
    }

    private StoreSnapshot resolveStore(Map<Long, Optional<StoreSnapshot>> storeSnapshotCache, Long storeId) {
        if (storeId == null || storeId <= 0L) {
            return null;
        }
        return storeSnapshotCache.computeIfAbsent(storeId, storeSnapshotClient::findStore).orElse(null);
    }

    private Integer resolveAvailableStock(Long itemId) {
        return stockSummaryClient.findByItemId(itemId)
                .map(StockSummary::availableQuantity)
                .orElse(null);
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_BATCH_SIZE;
        }
        return Math.min(size, MAX_BATCH_SIZE);
    }

    private int normalizeMaxPages(Integer maxPages) {
        if (maxPages == null || maxPages <= 0) {
            return DEFAULT_MAX_PAGES;
        }
        return Math.min(maxPages, MAX_MAX_PAGES);
    }

    private String trimToNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim();
    }
}
