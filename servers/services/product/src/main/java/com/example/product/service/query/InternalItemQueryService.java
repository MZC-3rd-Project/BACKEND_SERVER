package com.example.product.service.query;

import com.example.core.exception.BusinessException;
import com.example.data.entity.datasource.UseWriteDataSource;
import com.example.product.dto.item.response.InternalStoreItemSummaryResponse;
import com.example.product.dto.item.response.ItemContentSnapshot;
import com.example.product.dto.item.response.ItemSearchDocumentResponse;
import com.example.product.dto.item.response.ItemSummaryResponse;
import com.example.product.entity.image.ItemImage;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.service.content.ItemContentService;
import com.example.product.service.query.detail.ItemCategoryDetailResolver;
import com.example.product.service.query.detail.ItemCategoryDetailView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@UseWriteDataSource
public class InternalItemQueryService {

    private final ItemRepository itemRepository;
    private final ItemImageRepository itemImageRepository;
    private final ItemAccessPolicy itemAccessPolicy;
    private final ItemContentService itemContentService;
    private final ItemCategoryDetailResolver itemCategoryDetailResolver;

    public ItemSummaryResponse findById(Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));
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

    public List<ItemSummaryResponse> findItemsEndingSoon() {
        // MVP에서는 마감일 컬럼이 없어 ON_SALE 상태를 후보로 반환한다.
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

    public List<InternalStoreItemSummaryResponse> findByStoreId(Long storeId) {
        return itemRepository.findByStoreIdAndStatusInOrderByUpdatedAtDesc(storeId, itemAccessPolicy.visibleStatuses()).stream()
            .map(InternalStoreItemSummaryResponse::from)
            .toList();
    }

    public ItemSearchDocumentResponse findSearchDocument(Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));

        ItemCategoryDetailView categoryDetail = itemCategoryDetailResolver.resolve(List.of(item)).get(item.getId());
        ItemContentSnapshot contentSnapshot = itemContentService.findByItemId(itemId);
        return ItemSearchDocumentResponse.from(item, categoryDetail, contentSnapshot);
    }

    public List<ItemSearchDocumentResponse> findSearchDocuments(List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }

        List<Long> distinctItemIds = itemIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctItemIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Item> itemMap = new LinkedHashMap<>();
        for (Item item : itemRepository.findAllById(distinctItemIds)) {
            itemMap.put(item.getId(), item);
        }

        Map<Long, ItemCategoryDetailView> categoryDetailMap = itemCategoryDetailResolver.resolve(List.copyOf(itemMap.values()));
        Map<Long, ItemContentSnapshot> contentSnapshotMap = itemContentService.findByItemIds(distinctItemIds);

        return distinctItemIds.stream()
                .map(itemMap::get)
                .filter(Objects::nonNull)
                .map(item -> ItemSearchDocumentResponse.from(
                        item,
                        categoryDetailMap.get(item.getId()),
                        contentSnapshotMap.getOrDefault(item.getId(), ItemContentSnapshot.empty())
                ))
                .toList();
    }
}
