package com.example.storequery.service.query;

import com.example.storequery.entity.StoreQueryStatus;

public interface StoreSummarySource {

    Long storeId();

    Long userId();

    String storeName();

    StoreQueryStatus status();

    String description();

    String primaryContactValue();

    String defaultAddress();

    String ownerNickname();

    Long thumbnailMediaId();

    String thumbnailUrl();

    Integer thumbnailSortOrder();
}
