package com.example.product.service;

import com.example.core.exception.BusinessException;
import com.example.product.domain.goods.*;
import com.example.product.domain.item.Item;
import com.example.product.domain.item.ItemRepository;
import com.example.product.domain.item.ItemType;
import com.example.product.dto.goods.*;
import com.example.product.exception.ProductErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository itemRepository;
    private final ItemOptionRepository itemOptionRepository;
    private final ShippingInfoRepository shippingInfoRepository;
    private final ItemGoodsLinkRepository itemGoodsLinkRepository;

    // ─── 굿즈 ─────────────────────────────────────────────

    @Transactional
    public GoodsDetailResponse createGoods(GoodsCreateRequest request, Long sellerId) {
        Item item = Item.create(
                request.getTitle(), request.getDescription(), request.getPrice(),
                ItemType.GOODS, request.getCategoryId(), sellerId, request.getThumbnailUrl());
        itemRepository.save(item);

        List<ItemOption> options = saveOptions(item.getId(), request.getOptions());

        ShippingInfo shippingInfo = null;
        if (request.getShippingInfo() != null) {
            shippingInfo = saveShippingInfo(item.getId(), request.getShippingInfo());
        }

        List<Long> linkedIds = List.of();
        if (request.getLinkedPerformanceItemIds() != null) {
            linkedIds = linkPerformances(item.getId(), request.getLinkedPerformanceItemIds());
        }

        return GoodsDetailResponse.of(item, options, shippingInfo, linkedIds);
    }

    public GoodsDetailResponse findGoodsById(Long itemId) {
        Item item = getItem(itemId);
        List<ItemOption> options = itemOptionRepository.findByItemId(itemId);
        ShippingInfo shippingInfo = shippingInfoRepository.findByItemId(itemId).orElse(null);
        List<Long> linkedIds = itemGoodsLinkRepository.findByGoodsItemId(itemId).stream()
                .map(ItemGoodsLink::getPerformanceItemId).toList();
        return GoodsDetailResponse.of(item, options, shippingInfo, linkedIds);
    }

    public List<GoodsDetailResponse> findGoodsList(Long cursor, int size) {
        PageRequest pageable = PageRequest.of(0, size);
        List<Item> items = cursor == null
                ? itemRepository.findByItemTypeOrderByIdDesc(ItemType.GOODS, pageable)
                : itemRepository.findByItemTypeAndIdLessThan(ItemType.GOODS, cursor, pageable);

        return items.stream().map(item -> {
            List<ItemOption> options = itemOptionRepository.findByItemId(item.getId());
            ShippingInfo shippingInfo = shippingInfoRepository.findByItemId(item.getId()).orElse(null);
            List<Long> linkedIds = itemGoodsLinkRepository.findByGoodsItemId(item.getId()).stream()
                    .map(ItemGoodsLink::getPerformanceItemId).toList();
            return GoodsDetailResponse.of(item, options, shippingInfo, linkedIds);
        }).toList();
    }

    // ─── 일반상품 ─────────────────────────────────────────

    @Transactional
    public GoodsDetailResponse createProduct(ProductCreateRequest request, Long sellerId) {
        Item item = Item.create(
                request.getTitle(), request.getDescription(), request.getPrice(),
                ItemType.PRODUCT, request.getCategoryId(), sellerId, request.getThumbnailUrl());
        itemRepository.save(item);

        List<ItemOption> options = saveOptions(item.getId(), request.getOptions());
        ShippingInfo shippingInfo = saveShippingInfo(item.getId(), request.getShippingInfo());

        return GoodsDetailResponse.of(item, options, shippingInfo, List.of());
    }

    public GoodsDetailResponse findProductById(Long itemId) {
        return findGoodsById(itemId);
    }

    public List<GoodsDetailResponse> findProductList(Long cursor, int size) {
        PageRequest pageable = PageRequest.of(0, size);
        List<Item> items = cursor == null
                ? itemRepository.findByItemTypeOrderByIdDesc(ItemType.PRODUCT, pageable)
                : itemRepository.findByItemTypeAndIdLessThan(ItemType.PRODUCT, cursor, pageable);

        return items.stream().map(item -> {
            List<ItemOption> options = itemOptionRepository.findByItemId(item.getId());
            ShippingInfo shippingInfo = shippingInfoRepository.findByItemId(item.getId()).orElse(null);
            return GoodsDetailResponse.of(item, options, shippingInfo, List.of());
        }).toList();
    }

    // ─── 공통 헬퍼 ────────────────────────────────────────

    private Item getItem(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));
    }

    private List<ItemOption> saveOptions(Long itemId, List<ItemOptionRequest> requests) {
        if (requests == null || requests.isEmpty()) return List.of();
        List<ItemOption> options = requests.stream()
                .map(req -> ItemOption.create(itemId, req.getOptionName(),
                        req.getAdditionalPrice(), req.getStockQuantity()))
                .toList();
        return itemOptionRepository.saveAll(options);
    }

    private ShippingInfo saveShippingInfo(Long itemId, ShippingInfoRequest request) {
        ShippingInfo si = ShippingInfo.create(itemId, request.getShippingFee(),
                request.getFreeShippingThreshold(), request.getEstimatedDays(), request.getReturnPolicy());
        return shippingInfoRepository.save(si);
    }

    private List<Long> linkPerformances(Long goodsItemId, List<Long> performanceItemIds) {
        List<ItemGoodsLink> links = performanceItemIds.stream()
                .map(perfId -> ItemGoodsLink.create(perfId, goodsItemId))
                .toList();
        itemGoodsLinkRepository.saveAll(links);
        return performanceItemIds;
    }
}
