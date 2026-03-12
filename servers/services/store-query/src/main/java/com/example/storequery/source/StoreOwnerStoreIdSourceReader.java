package com.example.storequery.source;

import java.util.List;

public interface StoreOwnerStoreIdSourceReader {

    List<Long> readStoreIdsByUserId(Long userId);
}
