package com.example.product.service.query.detail;

import com.example.product.dto.item.response.ItemContentSnapshot;
import com.example.product.dto.item.response.ItemSummaryResponse;
import com.example.product.entity.goods.ItemOption;
import com.example.product.entity.goods.ShippingInfo;
import com.example.product.entity.image.ItemImage;
import com.example.product.entity.item.Item;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemOptionRepository;
import com.example.product.repository.ShippingInfoRepository;
import com.example.product.service.content.ItemContentService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

abstract class AbstractGoodsItemDetailReader implements ItemDetailReader<GoodsItemDetailView> {

    private final ItemRepository itemRepository;
    private final ItemOptionRepository itemOptionRepository;
    private final ShippingInfoRepository shippingInfoRepository;
    private final ItemImageRepository itemImageRepository;
    private final ItemContentService itemContentService;
    private final ItemCategoryDetailResolver itemCategoryDetailResolver;

    protected AbstractGoodsItemDetailReader(ItemRepository itemRepository,
                                            ItemOptionRepository itemOptionRepository,
                                            ShippingInfoRepository shippingInfoRepository,
                                            ItemImageRepository itemImageRepository,
                                            ItemContentService itemContentService,
                                            ItemCategoryDetailResolver itemCategoryDetailResolver) {
        this.itemRepository = itemRepository;
        this.itemOptionRepository = itemOptionRepository;
        this.shippingInfoRepository = shippingInfoRepository;
        this.itemImageRepository = itemImageRepository;
        this.itemContentService = itemContentService;
        this.itemCategoryDetailResolver = itemCategoryDetailResolver;
    }

    @Override
    public GoodsItemDetailView read(Item item) {
        return readAll(List.of(item)).get(item.getId());
    }

    @Override
    public Map<Long, GoodsItemDetailView> readAll(List<Item> items) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }

        List<Long> itemIds = items.stream().map(Item::getId).toList();
        Map<Long, List<ItemOption>> optionsMap = itemOptionRepository.findByItemIdIn(itemIds).stream()
                .collect(Collectors.groupingBy(ItemOption::getItemId));
        Map<Long, ShippingInfo> shippingInfoMap = shippingInfoRepository.findByItemIdIn(itemIds).stream()
                .collect(Collectors.toMap(ShippingInfo::getItemId, shippingInfo -> shippingInfo));
        Map<Long, List<ItemImage>> imageMap = itemImageRepository.findByItemIdInOrderByItemIdAscSortOrderAsc(itemIds).stream()
                .collect(Collectors.groupingBy(ItemImage::getItemId));
        Map<Long, ItemContentSnapshot> contentMap = itemContentService.findByItemIds(itemIds);
        Map<Long, ItemCategoryDetailView> categoryDetailMap = itemCategoryDetailResolver.resolve(items);
        Map<Long, List<Long>> linkedPerformanceIdsMap = findLinkedPerformanceIds(itemIds);
        Map<Long, List<ItemSummaryResponse>> linkedPerformanceItemsMap = readLinkedPerformanceItems(linkedPerformanceIdsMap);

        Map<Long, GoodsItemDetailView> detailViews = new LinkedHashMap<>();
        for (Item item : items) {
            detailViews.put(item.getId(), new GoodsItemDetailView(
                    item,
                    categoryDetailMap.get(item.getId()),
                    optionsMap.getOrDefault(item.getId(), List.of()),
                    shippingInfoMap.get(item.getId()),
                    linkedPerformanceIdsMap.getOrDefault(item.getId(), List.of()),
                    linkedPerformanceItemsMap.getOrDefault(item.getId(), List.of()),
                    contentMap.getOrDefault(item.getId(), ItemContentSnapshot.empty()),
                    imageMap.getOrDefault(item.getId(), List.of())
            ));
        }
        return detailViews;
    }

    private Map<Long, List<ItemSummaryResponse>> readLinkedPerformanceItems(Map<Long, List<Long>> linkedPerformanceIdsMap) {
        List<Long> linkedItemIds = linkedPerformanceIdsMap.values().stream()
                .flatMap(List::stream)
                .distinct()
                .toList();

        if (linkedItemIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Item> linkedItemMap = itemRepository.findAllById(linkedItemIds).stream()
                .filter(Item::isPubliclyVisible)
                .collect(Collectors.toMap(Item::getId, item -> item));
        Map<Long, List<ItemImage>> linkedImageMap = itemImageRepository.findByItemIdInOrderByItemIdAscSortOrderAsc(linkedItemIds).stream()
                .collect(Collectors.groupingBy(ItemImage::getItemId));

        Map<Long, List<ItemSummaryResponse>> linkedItemsByOwner = new LinkedHashMap<>();
        for (Map.Entry<Long, List<Long>> entry : linkedPerformanceIdsMap.entrySet()) {
            List<ItemSummaryResponse> previews = entry.getValue().stream()
                    .map(linkedItemMap::get)
                    .filter(item -> item != null)
                    .map(item -> ItemSummaryResponse.from(item, linkedImageMap.getOrDefault(item.getId(), List.of())))
                    .toList();
            linkedItemsByOwner.put(entry.getKey(), previews);
        }
        return linkedItemsByOwner;
    }

    protected abstract Map<Long, List<Long>> findLinkedPerformanceIds(List<Long> itemIds);
}
