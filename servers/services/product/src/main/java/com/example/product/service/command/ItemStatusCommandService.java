package com.example.product.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.product.dto.item.request.StatusChangeRequest;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.entity.item.ItemStatusHistory;
import com.example.product.event.ItemStatusChangedEvent;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.ItemStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ItemStatusCommandService {

    private final ItemRepository itemRepository;
    private final ItemStatusHistoryRepository statusHistoryRepository;
    private final EventPublisher eventPublisher;

    public void changeStatus(Long itemId, StatusChangeRequest request, Long userId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));

        item.validateOwnership(userId);

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
}
