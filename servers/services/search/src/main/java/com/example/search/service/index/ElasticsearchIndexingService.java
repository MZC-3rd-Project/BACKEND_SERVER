package com.example.search.service.index;

import com.example.search.client.ProductSearchSourceClient;
import com.example.search.client.StockSummaryClient;
import com.example.search.client.StoreSnapshotClient;
import com.example.search.client.dto.ProductSearchDocument;
import com.example.search.client.dto.StockSummary;
import com.example.search.client.dto.StoreSnapshot;
import com.example.search.document.ItemDocument;
import com.example.search.service.enrichment.SearchAiEnrichmentTaskPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ElasticsearchIndexingService implements SearchIndexingService {

    private final ProductSearchSourceClient productSearchSourceClient;
    private final StockSummaryClient stockSummaryClient;
    private final StoreSnapshotClient storeSnapshotClient;
    private final ElasticsearchDocumentClient elasticsearchDocumentClient;
    private final SearchAiEnrichmentTaskPublisher searchAiEnrichmentTaskPublisher;

    @Override
    public void upsertItem(Long itemId) {
        Optional<ProductSearchDocument> productDocument = productSearchSourceClient.findSearchDocument(itemId);
        if (productDocument.isEmpty()) {
            elasticsearchDocumentClient.delete(itemId);
            return;
        }

        StoreSnapshot storeSnapshot = resolveStore(productDocument.get().storeId());
        Integer availableStock = resolveAvailableStock(itemId);
        elasticsearchDocumentClient.upsert(ItemDocument.from(productDocument.get(), storeSnapshot, availableStock));
        searchAiEnrichmentTaskPublisher.publish(productDocument.get(), "ITEM_UPDATED");
    }

    @Override
    public void updateAvailableStock(Long itemId, Integer availableStock) {
        elasticsearchDocumentClient.updateAvailableStock(itemId, availableStock);
    }

    @Override
    public void deleteItem(Long itemId) {
        elasticsearchDocumentClient.delete(itemId);
    }

    @Override
    public void reindexStore(Long storeId) {
        List<Long> itemIds = productSearchSourceClient.findStoreItemIds(storeId);
        if (itemIds.isEmpty()) {
            return;
        }

        StoreSnapshot storeSnapshot = resolveStore(storeId);
        List<ProductSearchDocument> documents = productSearchSourceClient.findSearchDocuments(itemIds);
        for (ProductSearchDocument document : documents) {
            Integer availableStock = resolveAvailableStock(document.itemId());
            elasticsearchDocumentClient.upsert(ItemDocument.from(document, storeSnapshot, availableStock));
            searchAiEnrichmentTaskPublisher.publish(document, "STORE_REINDEX");
        }
    }

    private StoreSnapshot resolveStore(Long storeId) {
        return storeSnapshotClient.findStore(storeId).orElse(null);
    }

    private Integer resolveAvailableStock(Long itemId) {
        return stockSummaryClient.findByItemId(itemId)
                .map(StockSummary::availableQuantity)
                .orElse(null);
    }
}
