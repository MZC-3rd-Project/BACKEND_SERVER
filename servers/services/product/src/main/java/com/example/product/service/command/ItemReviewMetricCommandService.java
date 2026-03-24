package com.example.product.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.product.dto.item.request.ReviewMetricsUpdateRequest;
import com.example.product.entity.item.Item;
import com.example.product.event.ItemUpdatedEvent;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional
public class ItemReviewMetricCommandService {

    private final ItemRepository itemRepository;
    private final EventPublisher eventPublisher;

    public void updateReviewMetrics(Long itemId, ReviewMetricsUpdateRequest request) {
        updateReviewMetrics(itemId, request.getAverageRating(), request.getReviewCount());
    }

    public void updateReviewMetrics(Long itemId, BigDecimal averageRating, Long reviewCount) {
        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));

        item.updateReviewMetrics(averageRating, reviewCount);

        eventPublisher.publish(
                new ItemUpdatedEvent(
                        item.getId(),
                        item.getTitle(),
                        item.getPrice(),
                        item.getThumbnailMediaId(),
                        System.currentTimeMillis(),
                        item.getItemType().name(),
                        item.getStatus().name(),
                        item.getSellerId(),
                        item.getStoreId(),
                        item.getAverageRating(),
                        item.getReviewCount()
                ),
                EventMetadata.of("Item", String.valueOf(item.getId()))
        );
    }
}
