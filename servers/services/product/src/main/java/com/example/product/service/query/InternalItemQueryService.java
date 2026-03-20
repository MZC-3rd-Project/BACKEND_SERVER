package com.example.product.service.query;

import com.example.core.exception.BusinessException;
import com.example.core.pagination.CursorResponse;
import com.example.core.pagination.CursorUtils;
import com.example.data.entity.datasource.UseWriteDataSource;
import com.example.product.dto.item.response.InternalStoreItemSummaryResponse;
import com.example.product.dto.item.response.ItemContentSnapshot;
import com.example.product.dto.item.response.ItemSearchDocumentResponse;
import com.example.product.dto.item.response.ItemSummaryResponse;
import com.example.product.entity.image.ItemImage;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.entity.item.ItemType;
import com.example.product.entity.performance.Performance;
import com.example.product.entity.performance.SeatGrade;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemOptionRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.PerformanceRepository;
import com.example.product.repository.SeatGradeRepository;
import com.example.product.service.content.ItemContentService;
import com.example.product.service.query.detail.ItemCategoryDetailResolver;
import com.example.product.service.query.detail.ItemCategoryDetailView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
    private final ItemOptionRepository itemOptionRepository;
    private final PerformanceRepository performanceRepository;
    private final SeatGradeRepository seatGradeRepository;
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
        Integer stock = resolveTotalStock(List.of(item)).get(item.getId());
        return ItemSearchDocumentResponse.from(item, categoryDetail, contentSnapshot, stock);
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
        Map<Long, Integer> stockMap = resolveTotalStock(List.copyOf(itemMap.values()));

        return distinctItemIds.stream()
                .map(itemMap::get)
                .filter(Objects::nonNull)
                .map(item -> ItemSearchDocumentResponse.from(
                        item,
                        categoryDetailMap.get(item.getId()),
                        contentSnapshotMap.getOrDefault(item.getId(), ItemContentSnapshot.empty()),
                        stockMap.get(item.getId())
                ))
                .toList();
    }

    public CursorResponse<ItemSearchDocumentResponse> findSearchDocuments(String cursor, int size) {
        Long cursorId = CursorUtils.decodeLong(cursor);
        int normalizedSize = normalizeSize(size);
        PageRequest pageable = PageRequest.of(0, normalizedSize + 1);
        List<ItemStatus> visibleStatuses = itemAccessPolicy.visibleStatuses();

        List<Item> items = cursorId == null
                ? itemRepository.findByStatusInOrderByIdDesc(visibleStatuses, pageable)
                : itemRepository.findByStatusInAndIdLessThanOrderByIdDesc(visibleStatuses, cursorId, pageable);

        boolean hasNext = items.size() > normalizedSize;
        List<Item> pageItems = hasNext ? items.subList(0, normalizedSize) : items;

        Map<Long, ItemCategoryDetailView> categoryDetailMap = itemCategoryDetailResolver.resolve(pageItems);
        Map<Long, ItemContentSnapshot> contentSnapshotMap = itemContentService.findByItemIds(
                pageItems.stream().map(Item::getId).toList()
        );
        Map<Long, Integer> stockMap = resolveTotalStock(pageItems);

        List<ItemSearchDocumentResponse> content = pageItems.stream()
                .map(item -> ItemSearchDocumentResponse.from(
                        item,
                        categoryDetailMap.get(item.getId()),
                        contentSnapshotMap.getOrDefault(item.getId(), ItemContentSnapshot.empty()),
                        stockMap.get(item.getId())
                ))
                .toList();

        String nextCursor = hasNext
                ? CursorUtils.encode(pageItems.get(pageItems.size() - 1).getId())
                : null;
        return CursorResponse.of(content, nextCursor);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 100;
        }
        return Math.min(size, 500);
    }

    private Map<Long, Integer> resolveTotalStock(List<Item> items) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }

        Map<Long, Integer> totalStockByItemId = new LinkedHashMap<>();

        List<Long> optionItemIds = items.stream()
                .filter(item -> item.getItemType() == ItemType.PRODUCT || item.getItemType() == ItemType.GOODS)
                .map(Item::getId)
                .toList();

        if (!optionItemIds.isEmpty()) {
            itemOptionRepository.findByItemIdIn(optionItemIds).forEach(option ->
                    totalStockByItemId.merge(option.getItemId(), option.getStockQuantity(), Integer::sum));
        }

        List<Item> performanceItems = items.stream()
                .filter(item -> item.getItemType() == ItemType.PERFORMANCE)
                .toList();

        if (!performanceItems.isEmpty()) {
            Map<Long, Performance> performanceByItemId = performanceRepository.findByItemIdIn(
                            performanceItems.stream().map(Item::getId).toList())
                    .stream()
                    .collect(Collectors.toMap(Performance::getItemId, performance -> performance));

            Map<Long, Integer> seatGradeTotalsByPerformanceId = seatGradeRepository.findByPerformanceIdIn(
                            performanceByItemId.values().stream().map(Performance::getId).toList())
                    .stream()
                    .collect(Collectors.groupingBy(
                            SeatGrade::getPerformanceId,
                            Collectors.summingInt(SeatGrade::getTotalQuantity)
                    ));

            performanceItems.forEach(item -> {
                Performance performance = performanceByItemId.get(item.getId());
                if (performance == null) {
                    return;
                }

                Integer totalStock = seatGradeTotalsByPerformanceId.get(performance.getId());
                if (totalStock == null) {
                    totalStock = performance.getTotalSeats();
                }
                if (totalStock != null) {
                    totalStockByItemId.put(item.getId(), totalStock);
                }
            });
        }

        return totalStockByItemId;
    }
}
