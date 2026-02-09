package com.example.product.service;

import com.example.core.exception.BusinessException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.product.domain.item.*;
import com.example.product.dto.item.StatusChangeRequest;
import com.example.product.dto.item.StatusHistoryResponse;
import com.example.product.event.ItemStatusChangedEvent;
import com.example.product.exception.ProductErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemStatusService {

    private final ItemRepository itemRepository;
    private final ItemStatusHistoryRepository statusHistoryRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    public void changeStatus(Long itemId, StatusChangeRequest request, Long userId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));

        ItemStatus previousStatus = item.getStatus();
        ItemStatus newStatus = ItemStatus.valueOf(request.getStatus());

        item.changeStatus(newStatus);

        ItemStatusHistory history = ItemStatusHistory.create(
                itemId, previousStatus, newStatus, request.getReason(), userId);
        statusHistoryRepository.save(history);

        ItemStatusChangedEvent event = new ItemStatusChangedEvent(
                itemId, previousStatus.name(), newStatus.name());
        eventPublisher.publish(event, EventMetadata.of("Item", String.valueOf(itemId)));

        log.info("[ItemStatus] {} -> {} for item #{}", previousStatus, newStatus, itemId);
    }

    public List<StatusHistoryResponse> getHistory(Long itemId) {
        return statusHistoryRepository.findByItemIdOrderByCreatedAtDesc(itemId).stream()
                .map(StatusHistoryResponse::from).toList();
    }
}
