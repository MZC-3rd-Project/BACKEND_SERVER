package com.example.storequery.source;

import java.util.Optional;

public interface StoreSourceSnapshotReader {

    Optional<StoreSourceSnapshot> read(Long storeId);
}
