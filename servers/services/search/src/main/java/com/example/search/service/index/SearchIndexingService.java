package com.example.search.service.index;

public interface SearchIndexingService {

    void upsertItem(Long itemId);

    void deleteItem(Long itemId);

    void reindexStore(Long storeId);
}
