package com.example.product.service.query;

import com.example.product.dto.item.response.ItemSummaryResponse;
import com.example.product.entity.image.ItemImage;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalItemQueryService {

    private final ItemRepository itemRepository;
    private final ItemImageRepository itemImageRepository;

    public ItemSummaryResponse findById(Long itemId) {
        Item item = itemRepository.findById(itemId).orElse(null);
        if (item == null) return null;
        List<ItemImage> images = itemImageRepository.findByItemIdOrderBySortOrder(itemId);
        return ItemSummaryResponse.from(item, images);
    }

    public List<ItemSummaryResponse> findByIds(List<Long> itemIds) {
        List<Item> items = itemRepository.findAllById(itemIds);
        Map<Long, List<ItemImage>> imageMap = itemImageRepository
                .findByItemIdInOrderByItemIdAscSortOrderAsc(itemIds).stream()
                .collect(Collectors.groupingBy(ItemImage::getItemId));
        return items.stream()
                .map(item -> ItemSummaryResponse.from(item, imageMap.getOrDefault(item.getId(), List.of())))
                .toList();
    }

    public List<ItemSummaryResponse> findItemsEndingSoon(int withinDays) {
        // For MVP, return ON_SALE items as hot-deal candidates
        // Future: filter by actual end date when the field is added
        List<Item> items = itemRepository.findByStatusIn(
                List.of(ItemStatus.ON_SALE), Pageable.ofSize(50));
        List<Long> itemIds = items.stream().map(Item::getId).toList();
        Map<Long, List<ItemImage>> imageMap = itemImageRepository
                .findByItemIdInOrderByItemIdAscSortOrderAsc(itemIds).stream()
                .collect(Collectors.groupingBy(ItemImage::getItemId));
        return items.stream()
                .map(item -> ItemSummaryResponse.from(item, imageMap.getOrDefault(item.getId(), List.of())))
                .toList();
    }
}
