package com.example.storequery.service;

import com.example.storequery.projection.StoreReadModelSnapshot;

public interface StoreReadModelWriter {

    void upsert(StoreReadModelSnapshot snapshot);

    void softDeleteByStoreId(Long storeId);
}
