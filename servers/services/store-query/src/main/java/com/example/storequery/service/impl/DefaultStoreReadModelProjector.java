package com.example.storequery.service.impl;

import com.example.storequery.projection.StoreReadModelSnapshot;
import com.example.storequery.service.StoreReadModelProjector;
import com.example.storequery.service.StoreReadModelSnapshotReader;
import com.example.storequery.service.StoreReadModelWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DefaultStoreReadModelProjector implements StoreReadModelProjector {

    private final StoreReadModelSnapshotReader snapshotReader;
    private final StoreReadModelWriter writer;

    @Override
    public void project(Long storeId) {
        if (storeId == null || storeId <= 0L) {
            throw new IllegalArgumentException("storeId must be positive");
        }

        StoreReadModelSnapshot snapshot = snapshotReader.read(storeId).orElse(null);
        if (snapshot == null) {
            writer.softDeleteByStoreId(storeId);
            return;
        }
        writer.upsert(snapshot);
    }
}
