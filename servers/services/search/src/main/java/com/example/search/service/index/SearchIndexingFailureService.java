package com.example.search.service.index;

import com.example.core.exception.BusinessException;
import com.example.core.util.JsonUtils;
import com.example.search.consumer.FundingEventMessage;
import com.example.search.consumer.HotDealEventMessage;
import com.example.search.consumer.ItemEventMessage;
import com.example.search.consumer.StockEventMessage;
import com.example.search.dto.index.response.IndexingFailureRetryResponse;
import com.example.search.entity.SearchIndexingFailure;
import com.example.search.entity.SearchIndexingFailureStatus;
import com.example.search.exception.SearchErrorCode;
import com.example.search.repository.SearchIndexingFailureRepository;
import com.example.search.service.query.cache.SearchResultCacheService;
import com.example.search.service.thumbnail.SearchThumbnailEnrichmentTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexingFailureService {

    private static final String DEFAULT_ITEM_STATUS = "DRAFT";

    private final SearchIndexingFailureRepository failureRepository;
    private final SearchIndexingService searchIndexingService;
    private final SearchResultCacheService searchResultCacheService;
    private final SearchThumbnailEnrichmentTaskService thumbnailEnrichmentTaskService;

    @Transactional
    public void recordItemEventFailure(ItemEventMessage event, String rawMessage, Exception e) {
        String eventId = event == null ? null : event.getEventId();
        String eventType = event == null ? null : event.getEventType();
        Long itemId = event == null ? null : event.getItemId();
        recordFailure(eventId, eventType, itemId, rawMessage, e);
    }

    @Transactional
    public void recordStockEventFailure(StockEventMessage event, String rawMessage, Exception e) {
        String eventId = event == null ? null : event.getEventId();
        String eventType = event == null ? null : event.getEventType();
        Long itemId = event == null ? null : event.getItemId();
        recordFailure(eventId, eventType, itemId, rawMessage, e);
    }

    @Transactional
    public void recordHotDealEventFailure(HotDealEventMessage event, String rawMessage, Exception e) {
        String eventId = event == null ? null : event.getEventId();
        String eventType = event == null ? null : event.getEventType();
        Long itemId = event == null ? null : event.getItemId();
        recordFailure(eventId, eventType, itemId, rawMessage, e);
    }

    @Transactional
    public void recordFundingEventFailure(FundingEventMessage event, String rawMessage, Exception e) {
        String eventId = event == null ? null : event.getEventId();
        String eventType = event == null ? null : event.getEventType();
        Long itemId = event == null ? null : event.getItemId();
        recordFailure(eventId, eventType, itemId, rawMessage, e);
    }

    @Transactional
    public IndexingFailureRetryResponse retryFailure(Long failureId) {
        SearchIndexingFailure failure = failureRepository.findById(failureId)
                .orElseThrow(() -> new BusinessException(SearchErrorCode.SEARCH_INDEXING_FAILURE_NOT_FOUND));

        failure.markRetrying();
        failureRepository.save(failure);

        try {
            replay(failure.getEventType(), failure.getPayload());
            searchResultCacheService.evictAll();
            failure.markResolved();
            failureRepository.save(failure);
            return toResponse(failure);
        } catch (Exception e) {
            failure.markFailed(exceptionMessage(e));
            failureRepository.save(failure);
            throw e;
        }
    }

    private void replay(String eventType, String payload) {
        String normalizedType = normalizeEventType(eventType);

        switch (normalizedType) {
            case "ITEM_CREATED" -> replayItemCreated(payload);
            case "ITEM_UPDATED" -> replayItemUpdated(payload);
            case "ITEM_STATUS_CHANGED" -> replayItemStatusChanged(payload);
            case "ITEM_DELETED" -> replayItemDeleted(payload);
            case "STOCK_DECREASED" -> replayStockDecreased(payload);
            case "STOCK_INCREASED" -> replayStockIncreased(payload);
            case "ITEM_AVAILABLE_STOCK_CHANGED" -> replayItemAvailableStockChanged(payload);
            case "HOT_DEAL_STARTED" -> replayHotDealStarted(payload);
            case "HOT_DEAL_ENDED", "HOT_DEAL_CANCELLED" -> replayHotDealEnded(payload);
            case "FUNDING_CREATED" -> replayFundingCreated(payload);
            case "FUNDING_SUCCEEDED" -> replayFundingClosed(payload, "FUNDED");
            case "FUNDING_FAILED" -> replayFundingClosed(payload, "FUND_FAILED");
            case "FUNDING_CANCELLED" -> replayFundingClosed(payload, "CLOSED");
            default -> throw new BusinessException(SearchErrorCode.SEARCH_INDEXING_FAILED,
                    "지원하지 않는 재처리 이벤트 타입입니다. eventType=" + eventType);
        }
    }

    private void replayItemCreated(String payload) {
        ItemEventMessage event = JsonUtils.fromJson(payload, ItemEventMessage.class);
        Integer initialStock = sumStock(event.getStockItems());
        Long mediaVersion = resolveMediaVersion(event);
        searchIndexingService.indexItem(
                event.getItemId(),
                event.getTitle(),
                event.getItemType(),
                event.getItemType(),
                event.getPrice(),
                resolveInitialStatus(event),
                initialStock,
                event.getThumbnailMediaId(),
                mediaVersion
        );
        scheduleThumbnailEnrichment(event.getItemId(), event.getThumbnailMediaId(), mediaVersion);
    }

    private void replayItemUpdated(String payload) {
        ItemEventMessage event = JsonUtils.fromJson(payload, ItemEventMessage.class);
        Long mediaVersion = resolveMediaVersion(event);
        searchIndexingService.updateItem(
                event.getItemId(),
                event.getTitle(),
                event.getPrice(),
                event.getThumbnailMediaId(),
                mediaVersion
        );
        scheduleThumbnailEnrichment(event.getItemId(), event.getThumbnailMediaId(), mediaVersion);
    }

    private void replayItemStatusChanged(String payload) {
        ItemEventMessage event = JsonUtils.fromJson(payload, ItemEventMessage.class);
        searchIndexingService.updateItemStatus(event.getItemId(), event.getNewStatus());
    }

    private void replayItemDeleted(String payload) {
        ItemEventMessage event = JsonUtils.fromJson(payload, ItemEventMessage.class);
        searchIndexingService.deleteItem(event.getItemId());
        thumbnailEnrichmentTaskService.removeTask(event.getItemId());
    }

    private void replayStockDecreased(String payload) {
        StockEventMessage event = JsonUtils.fromJson(payload, StockEventMessage.class);
        searchIndexingService.updateItemStock(event.getItemId(), event.getRemainingQuantity());
    }

    private void replayStockIncreased(String payload) {
        StockEventMessage event = JsonUtils.fromJson(payload, StockEventMessage.class);
        searchIndexingService.updateItemStock(event.getItemId(), event.resolveLegacyStockQuantity());
    }

    private void replayItemAvailableStockChanged(String payload) {
        StockEventMessage event = JsonUtils.fromJson(payload, StockEventMessage.class);
        searchIndexingService.updateItemStockVersioned(
                event.getItemId(),
                event.getAvailableStockTotal(),
                event.getStockVersion()
        );
    }

    private void replayHotDealStarted(String payload) {
        HotDealEventMessage event = JsonUtils.fromJson(payload, HotDealEventMessage.class);
        searchIndexingService.applyHotDealStarted(
                event.getItemId(),
                event.getHotDealId(),
                event.getDiscountedPrice()
        );
    }

    private void replayHotDealEnded(String payload) {
        HotDealEventMessage event = JsonUtils.fromJson(payload, HotDealEventMessage.class);
        searchIndexingService.applyHotDealEnded(
                event.getItemId(),
                event.getHotDealId()
        );
    }

    private void replayFundingCreated(String payload) {
        FundingEventMessage event = JsonUtils.fromJson(payload, FundingEventMessage.class);
        searchIndexingService.applyFundingCreated(
                event.getItemId(),
                event.getCampaignId()
        );
    }

    private void replayFundingClosed(String payload, String terminalStatus) {
        FundingEventMessage event = JsonUtils.fromJson(payload, FundingEventMessage.class);
        searchIndexingService.applyFundingClosed(
                event.getItemId(),
                event.getCampaignId(),
                terminalStatus
        );
    }

    private void recordFailure(String eventId, String eventType, Long itemId, String payload, Exception e) {
        String safeEventId = StringUtils.hasText(eventId) ? eventId : "unknown";
        String safeEventType = StringUtils.hasText(eventType) ? normalizeEventType(eventType) : "UNKNOWN";
        String failureReason = exceptionMessage(e);

        Optional<SearchIndexingFailure> existing = failureRepository.findByEventIdAndEventType(safeEventId, safeEventType);
        SearchIndexingFailure failure = existing.orElseGet(() -> SearchIndexingFailure.builder()
                .eventId(safeEventId)
                .eventType(safeEventType)
                .itemId(itemId)
                .payload(payload)
                .retryCount(0)
                .status(SearchIndexingFailureStatus.PENDING)
                .failureReason(failureReason)
                .build());

        failure.markPending(failureReason);
        failureRepository.save(failure);
    }

    private IndexingFailureRetryResponse toResponse(SearchIndexingFailure failure) {
        return IndexingFailureRetryResponse.builder()
                .failureId(failure.getId())
                .eventId(failure.getEventId())
                .eventType(failure.getEventType())
                .status(failure.getStatus())
                .retryCount(failure.getRetryCount())
                .failureReason(failure.getFailureReason())
                .lastRetriedAt(failure.getLastRetriedAt())
                .build();
    }

    private String normalizeEventType(String eventType) {
        return eventType == null ? "" : eventType.trim().toUpperCase(Locale.ROOT);
    }

    private Integer sumStock(List<ItemEventMessage.StockItemPayload> stockItems) {
        if (stockItems == null || stockItems.isEmpty()) {
            return null;
        }
        return stockItems.stream()
                .map(ItemEventMessage.StockItemPayload::getTotalQuantity)
                .filter(quantity -> quantity != null && quantity >= 0)
                .reduce(0, Integer::sum);
    }

    private String exceptionMessage(Exception e) {
        if (e == null || !StringUtils.hasText(e.getMessage())) {
            return "unknown";
        }
        return e.getMessage();
    }

    private String resolveInitialStatus(ItemEventMessage event) {
        if (event == null) {
            return DEFAULT_ITEM_STATUS;
        }
        if (StringUtils.hasText(event.getStatus())) {
            return event.getStatus();
        }
        if (StringUtils.hasText(event.getNewStatus())) {
            return event.getNewStatus();
        }
        return DEFAULT_ITEM_STATUS;
    }

    private void scheduleThumbnailEnrichment(Long itemId, Long thumbnailMediaId, Long mediaVersion) {
        if (itemId == null) {
            return;
        }
        if (thumbnailMediaId == null || mediaVersion == null) {
            thumbnailEnrichmentTaskService.removeTask(itemId);
            return;
        }
        thumbnailEnrichmentTaskService.enqueue(itemId, thumbnailMediaId, mediaVersion);
    }

    private Long resolveMediaVersion(ItemEventMessage event) {
        if (event != null && event.getMediaVersion() != null && event.getMediaVersion() > 0) {
            return event.getMediaVersion();
        }
        return System.currentTimeMillis();
    }
}
