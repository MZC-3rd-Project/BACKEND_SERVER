package com.example.search.service.index;

public interface SearchIndexingService {

    void upsertItem(Long itemId);

    void updateAvailableStock(Long itemId, Integer availableStock);

    void deleteItem(Long itemId);

    void reindexStore(Long storeId);
}
