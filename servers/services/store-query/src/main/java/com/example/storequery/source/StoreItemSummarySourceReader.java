package com.example.storequery.source;

import java.util.List;

public interface StoreItemSummarySourceReader {

    List<StoreItemSummarySource> readByStoreId(Long storeId);
}
