package com.example.search.service.index;

public interface SearchIndexingService {

    void indexItem(Long itemId,
                   String title,
                   String category,
                   String domainType,
                   Long price,
                   String status,
                   Integer stock,
                   Long thumbnailMediaId,
                   Long mediaVersion);

    void updateItem(Long itemId, String title, Long price, Long thumbnailMediaId, Long mediaVersion);

    void updateItemStatus(Long itemId, String status);

    void updateItemStatusByEventTime(Long itemId, String status, Long eventOccurredAtMillis);

    void updateItemStock(Long itemId, Integer stock);

    void updateItemStockVersioned(Long itemId, Integer stock, Long stockVersion);

    void applyHotDealStarted(Long itemId, Long hotDealId, Long discountedPrice);

    void applyHotDealStartedByEventTime(Long itemId, Long hotDealId, Long discountedPrice, Long eventOccurredAtMillis);

    void applyHotDealEnded(Long itemId, Long hotDealId);

    void applyHotDealEndedByEventTime(Long itemId, Long hotDealId, Long eventOccurredAtMillis);

    void applyFundingCreated(Long itemId, Long campaignId);

    void applyFundingCreatedByEventTime(Long itemId, Long campaignId, Long eventOccurredAtMillis);

    void applyFundingClosed(Long itemId, Long campaignId, String terminalStatus);

    void applyFundingClosedByEventTime(Long itemId, Long campaignId, String terminalStatus, Long eventOccurredAtMillis);

    void updateThumbnailSnapshot(Long itemId, Long thumbnailMediaId, String thumbnailUrlSnapshot, Long mediaVersion);

    void deleteItem(Long itemId);
}
