package com.example.product.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.product.dto.goods.request.ItemOptionRequest;
import com.example.product.dto.goods.request.ProductCreateRequest;
import com.example.product.dto.goods.request.ProductUpdateRequest;
import com.example.product.dto.goods.request.ShippingInfoRequest;
import com.example.product.dto.goods.response.GoodsDetailResponse;
import com.example.product.entity.goods.ItemOption;
import com.example.product.entity.image.ItemImage;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemType;
import com.example.product.entity.goods.ShippingInfo;
import com.example.product.event.ItemCreatedEvent;
import com.example.product.event.ItemUpdatedEvent;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemOptionRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.ShippingInfoRepository;
import com.example.product.service.command.image.ItemThumbnailSyncService;
import com.example.product.service.command.image.MediaReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductCommandService {

    private final ItemRepository itemRepository;
    private final ItemOptionRepository itemOptionRepository;
    private final ShippingInfoRepository shippingInfoRepository;
    private final ItemImageRepository itemImageRepository;
    private final MediaReferenceService mediaReferenceService;
    private final ItemThumbnailSyncService itemThumbnailSyncService;
    private final EventPublisher eventPublisher;

    public GoodsDetailResponse createProduct(ProductCreateRequest request, Long sellerId, Long storeIdHeader) {
        // TODO: store-service 연동 후 sellerId-storeId 소유권 검증을 추가한다.
        validateStoreOwnership(request.getStoreId(), storeIdHeader);
        mediaReferenceService.resolveMediaUrl(request.getThumbnailMediaId());
        Item item = Item.create(
                request.getTitle(), request.getDescription(), request.getPrice(),
                ItemType.PRODUCT, request.getCategoryId(), sellerId, request.getStoreId(),
                request.getThumbnailMediaId());
        itemRepository.save(item);
        if (request.getThumbnailMediaId() != null) {
            itemThumbnailSyncService.syncAfterCommit(item.getId(), request.getThumbnailMediaId());
        }

        List<ItemOption> options = saveOptions(item.getId(), request.getOptions());

        ShippingInfo shippingInfo = null;
        if (request.getShippingInfo() != null) {
            shippingInfo = saveShippingInfo(item.getId(), request.getShippingInfo());
        }

        List<ItemCreatedEvent.StockItemInfo> stockItems = options.stream()
                .map(opt -> new ItemCreatedEvent.StockItemInfo(
                        "ITEM_OPTION", opt.getId(), opt.getStockQuantity()))
                .toList();

        eventPublisher.publish(
                new ItemCreatedEvent(
                        item.getId(),
                        item.getTitle(),
                        item.getItemType().name(),
                        item.getPrice(),
                        item.getThumbnailMediaId(),
                        System.currentTimeMillis(),
                        item.getStatus().name(),
                        sellerId,
                        request.getStoreId(),
                        stockItems
                ),
                EventMetadata.of("Item", String.valueOf(item.getId())));

        List<ItemImage> images = itemImageRepository.findByItemIdOrderBySortOrder(item.getId());
        return GoodsDetailResponse.of(item, options, shippingInfo, List.of(), images);
    }

    public GoodsDetailResponse updateProduct(Long itemId, ProductUpdateRequest request, Long sellerId, Long storeIdHeader) {
        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));
        item.validateOwnership(sellerId);
        validateItemType(item, ItemType.PRODUCT);
        validateStoreOwnership(item.getStoreId(), storeIdHeader);
        if (!item.isEditable()) {
            throw new BusinessException(ProductErrorCode.ITEM_NOT_EDITABLE);
        }

        boolean clearThumbnail = Boolean.TRUE.equals(request.getClearThumbnail());
        Long thumbnailMediaId = request.getThumbnailMediaId();
        if (clearThumbnail && thumbnailMediaId != null) {
            throw new BusinessException(ProductErrorCode.INVALID_THUMBNAIL_UPDATE_REQUEST);
        }

        if (clearThumbnail) {
            item.update(request.getTitle(), request.getDescription(), request.getPrice(),
                    request.getCategoryId(), null);
            item.clearThumbnail();
            itemThumbnailSyncService.syncAfterCommit(item.getId(), null, true);
        } else {
            mediaReferenceService.resolveMediaUrl(thumbnailMediaId);
            item.update(request.getTitle(), request.getDescription(), request.getPrice(),
                    request.getCategoryId(), thumbnailMediaId);
            if (thumbnailMediaId != null) {
                itemThumbnailSyncService.syncAfterCommit(item.getId(), thumbnailMediaId);
            }
        }

        List<ItemOption> options = List.of();
        if (request.getOptions() != null) {
            itemOptionRepository.softDeleteAllByItemId(itemId);
            options = saveOptions(itemId, request.getOptions());
        }

        ShippingInfo shippingInfo = null;
        if (request.getShippingInfo() != null) {
            shippingInfoRepository.softDeleteByItemId(itemId);
            shippingInfo = saveShippingInfo(itemId, request.getShippingInfo());
        }

        eventPublisher.publish(
                new ItemUpdatedEvent(
                        item.getId(),
                        item.getTitle(),
                        item.getPrice(),
                        item.getThumbnailMediaId(),
                        System.currentTimeMillis()
                ),
                EventMetadata.of("Item", String.valueOf(item.getId())));

        List<ItemImage> images = itemImageRepository.findByItemIdOrderBySortOrder(itemId);
        return GoodsDetailResponse.of(item, options, shippingInfo, List.of(), images);
    }

    public void delete(Long itemId, Long sellerId, Long storeIdHeader) {
        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));
        item.validateOwnership(sellerId);
        validateItemType(item, ItemType.PRODUCT);
        validateStoreOwnership(item.getStoreId(), storeIdHeader);
        if (!item.isDeletable()) {
            throw new BusinessException(ProductErrorCode.ITEM_NOT_DELETABLE);
        }
        item.softDelete();

        itemOptionRepository.softDeleteAllByItemId(itemId);
        shippingInfoRepository.softDeleteByItemId(itemId);
        itemImageRepository.softDeleteAllByItemId(itemId);
        item.clearThumbnail();
        itemThumbnailSyncService.syncAfterCommit(itemId, null, true);
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

    private void validateItemType(Item item, ItemType expectedType) {
        if (item.getItemType() != expectedType) {
            throw new BusinessException(ProductErrorCode.ITEM_TYPE_MISMATCH);
        }
    }

    private void validateStoreOwnership(Long expectedStoreId, Long requestStoreId) {
        if (!Objects.equals(expectedStoreId, requestStoreId)) {
            throw new BusinessException(ProductErrorCode.STORE_OWNERSHIP_MISMATCH);
        }
    }

}
