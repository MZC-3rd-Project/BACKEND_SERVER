package com.example.search.service.index;

import com.example.core.exception.BusinessException;
import com.example.core.util.JsonUtils;
import com.example.search.consumer.ItemEventMessage;
import com.example.search.consumer.StockEventMessage;
import com.example.search.dto.index.response.IndexingFailureRetryResponse;
import com.example.search.entity.SearchIndexingFailure;
import com.example.search.entity.SearchIndexingFailureStatus;
import com.example.search.exception.SearchErrorCode;
import com.example.search.repository.SearchIndexingFailureRepository;
import com.example.search.service.query.cache.SearchResultCacheService;
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

    private final SearchIndexingFailureRepository failureRepository;
    private final SearchIndexingService searchIndexingService;
    private final SearchResultCacheService searchResultCacheService;

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
            default -> throw new BusinessException(SearchErrorCode.SEARCH_INDEXING_FAILED,
                    "지원하지 않는 재처리 이벤트 타입입니다. eventType=" + eventType);
        }
    }

    private void replayItemCreated(String payload) {
        ItemEventMessage event = JsonUtils.fromJson(payload, ItemEventMessage.class);
        Integer initialStock = sumStock(event.getStockItems());
        searchIndexingService.indexItem(
                event.getItemId(),
                event.getTitle(),
                event.getItemType(),
                event.getPrice(),
                event.getNewStatus(),
                initialStock
        );
    }

    private void replayItemUpdated(String payload) {
        ItemEventMessage event = JsonUtils.fromJson(payload, ItemEventMessage.class);
        searchIndexingService.updateItem(event.getItemId(), event.getTitle(), event.getPrice());
    }

    private void replayItemStatusChanged(String payload) {
        ItemEventMessage event = JsonUtils.fromJson(payload, ItemEventMessage.class);
        searchIndexingService.updateItemStatus(event.getItemId(), event.getNewStatus());
    }

    private void replayItemDeleted(String payload) {
        ItemEventMessage event = JsonUtils.fromJson(payload, ItemEventMessage.class);
        searchIndexingService.deleteItem(event.getItemId());
    }

    private void replayStockDecreased(String payload) {
        StockEventMessage event = JsonUtils.fromJson(payload, StockEventMessage.class);
        searchIndexingService.updateItemStock(event.getItemId(), event.getRemainingQuantity());
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
}
