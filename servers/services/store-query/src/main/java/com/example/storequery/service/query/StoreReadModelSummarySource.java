package com.example.storequery.service.query;

import com.example.storequery.entity.StoreQueryStatus;
import com.example.storequery.entity.StoreReadModel;

public record StoreReadModelSummarySource(
    StoreReadModel model
) implements StoreSummarySource {

    @Override
    public Long storeId() {
        return model.getStoreId();
    }

    @Override
    public Long userId() {
        return model.getUserId();
    }

    @Override
    public String storeName() {
        return model.getStoreName();
    }

    @Override
    public StoreQueryStatus status() {
        return model.getStatus();
    }

    @Override
    public String description() {
        return model.getDescription();
    }

    @Override
    public String primaryContactValue() {
        return model.getPrimaryContactValue();
    }

    @Override
    public String defaultAddress() {
        return model.getDefaultAddress();
    }

    @Override
    public String ownerNickname() {
        return model.getOwnerNickname();
    }

    @Override
    public Long thumbnailMediaId() {
        return model.getThumbnailMediaId();
    }

    @Override
    public String thumbnailUrl() {
        return model.getThumbnailUrl();
    }

    @Override
    public Integer thumbnailSortOrder() {
        return model.getThumbnailSortOrder();
    }
}
