package com.example.storequery.service.query;

import com.example.storequery.entity.StoreQueryStatus;
import com.example.storequery.repository.StoreReadModelSearchRow;

import java.util.Locale;

public record StoreSearchRowSummarySource(
    StoreReadModelSearchRow row
) implements StoreSummarySource {

    @Override
    public Long storeId() {
        return row.getStoreId();
    }

    @Override
    public Long userId() {
        return row.getUserId();
    }

    @Override
    public String storeName() {
        return row.getStoreName();
    }

    @Override
    public StoreQueryStatus status() {
        return StoreQueryStatus.valueOf(row.getStatus().trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public String description() {
        return row.getDescription();
    }

    @Override
    public String primaryContactValue() {
        return row.getPrimaryContactValue();
    }

    @Override
    public String defaultAddress() {
        return row.getDefaultAddress();
    }

    @Override
    public String ownerNickname() {
        return row.getOwnerNickname();
    }

    @Override
    public Long thumbnailMediaId() {
        return row.getThumbnailMediaId();
    }

    @Override
    public String thumbnailUrl() {
        return row.getThumbnailUrl();
    }

    @Override
    public Integer thumbnailSortOrder() {
        return row.getThumbnailSortOrder();
    }
}
