package com.example.storequery.source;

import java.util.Optional;

public interface StoreOwnerSnapshotSourceReader {

    Optional<StoreOwnerSnapshot> readByUserId(Long userId);
}
