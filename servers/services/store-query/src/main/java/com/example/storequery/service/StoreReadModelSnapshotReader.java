package com.example.storequery.service;

import com.example.storequery.projection.StoreReadModelSnapshot;

import java.util.Optional;

public interface StoreReadModelSnapshotReader {

    Optional<StoreReadModelSnapshot> read(Long storeId);
}
