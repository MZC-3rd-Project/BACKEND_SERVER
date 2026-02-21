package com.example.search.service.index;

public interface SearchIndexingService {

    void indexItem(Long itemId,
                   String title,
                   String category,
                   String domainType,
                   Long price,
                   String status,
                   Integer stock);

    void updateItem(Long itemId, String title, Long price);

    void updateItemStatus(Long itemId, String status);

    void updateItemStock(Long itemId, Integer stock);

    void updateItemStockVersioned(Long itemId, Integer stock, Long stockVersion);

    void deleteItem(Long itemId);
}
