package com.example.product.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.product.dto.goods.request.GoodsCreateRequest;
import com.example.product.dto.goods.request.GoodsUpdateRequest;
import com.example.product.dto.goods.request.ItemOptionRequest;
import com.example.product.dto.goods.request.ShippingInfoRequest;
import com.example.product.dto.goods.response.GoodsDetailResponse;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemType;
import com.example.product.entity.goods.ItemGoodsLink;
import com.example.product.entity.goods.ItemOption;
import com.example.product.entity.goods.ShippingInfo;
import com.example.product.event.ItemCreatedEvent;
import com.example.product.event.ItemUpdatedEvent;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class GoodsCommandService {

    private final ItemRepository itemRepository;
    private final ItemOptionRepository itemOptionRepository;
    private final ShippingInfoRepository shippingInfoRepository;
    private final ItemGoodsLinkRepository itemGoodsLinkRepository;
    private final ItemImageRepository itemImageRepository;
    private final EventPublisher eventPublisher;

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

        eventPublisher.publish(
                new ItemCreatedEvent(item.getId(), item.getTitle(), item.getItemType().name(), sellerId),
                EventMetadata.of("Item", String.valueOf(item.getId())));

        return GoodsDetailResponse.of(item, options, shippingInfo, linkedIds);
    }

    public GoodsDetailResponse updateGoods(Long itemId, GoodsUpdateRequest request, Long sellerId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));
        item.validateOwnership(sellerId);
        if (!item.isEditable()) {
            throw new BusinessException(ProductErrorCode.ITEM_NOT_EDITABLE);
        }

        item.update(request.getTitle(), request.getDescription(), request.getPrice(),
                request.getCategoryId(), request.getThumbnailUrl());

        // 옵션 교체
        List<ItemOption> options = List.of();
        if (request.getOptions() != null) {
            itemOptionRepository.softDeleteAllByItemId(itemId);
            options = saveOptions(itemId, request.getOptions());
        }

        // 배송정보 교체
        ShippingInfo shippingInfo = null;
        if (request.getShippingInfo() != null) {
            shippingInfoRepository.softDeleteByItemId(itemId);
            shippingInfo = saveShippingInfo(itemId, request.getShippingInfo());
        }

        // 공연 연결 교체
        List<Long> linkedIds = List.of();
        if (request.getLinkedPerformanceItemIds() != null) {
            itemGoodsLinkRepository.softDeleteAllByGoodsItemId(itemId);
            linkedIds = linkPerformances(itemId, request.getLinkedPerformanceItemIds());
        }

        eventPublisher.publish(
                new ItemUpdatedEvent(item.getId(), item.getTitle(), item.getPrice()),
                EventMetadata.of("Item", String.valueOf(item.getId())));

        return GoodsDetailResponse.of(item, options, shippingInfo, linkedIds);
    }

    public void delete(Long itemId, Long sellerId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));
        item.validateOwnership(sellerId);
        item.softDelete();

        itemOptionRepository.softDeleteAllByItemId(itemId);
        shippingInfoRepository.softDeleteByItemId(itemId);
        itemGoodsLinkRepository.softDeleteAllByGoodsItemId(itemId);
        itemImageRepository.softDeleteAllByItemId(itemId);
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
        if (performanceItemIds.isEmpty()) return List.of();

        List<Item> targets = itemRepository.findAllById(performanceItemIds);
        if (targets.size() != performanceItemIds.size()) {
            throw new BusinessException(ProductErrorCode.INVALID_LINK_TARGET);
        }
        boolean allPerformance = targets.stream()
                .allMatch(item -> item.getItemType() == ItemType.PERFORMANCE);
        if (!allPerformance) {
            throw new BusinessException(ProductErrorCode.INVALID_LINK_TARGET);
        }

        List<ItemGoodsLink> links = performanceItemIds.stream()
                .map(perfId -> ItemGoodsLink.create(perfId, goodsItemId))
                .toList();
        itemGoodsLinkRepository.saveAll(links);
        return performanceItemIds;
    }
}
