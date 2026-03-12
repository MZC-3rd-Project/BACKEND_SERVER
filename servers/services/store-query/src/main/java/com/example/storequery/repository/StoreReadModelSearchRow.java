package com.example.storequery.repository;

import java.time.LocalDateTime;

public interface StoreReadModelSearchRow {

    Long getStoreId();

    Long getUserId();

    String getStoreName();

    String getStatus();

    String getDescription();

    String getPrimaryContactValue();

    String getDefaultAddress();

    String getOwnerNickname();

    Long getThumbnailMediaId();

    String getThumbnailUrl();

    Integer getThumbnailSortOrder();

    LocalDateTime getSourceUpdatedAt();

    Double getSortRank();
}
