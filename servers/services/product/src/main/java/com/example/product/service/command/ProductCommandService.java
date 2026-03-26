package com.example.product.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.product.dto.goods.request.ItemOptionRequest;
import com.example.product.dto.goods.request.ProductCreateRequest;
import com.example.product.dto.goods.request.ProductUpdateRequest;
import com.example.product.dto.goods.request.ShippingInfoRequest;
import com.example.product.dto.goods.response.GoodsDetailResponse;
import com.example.product.dto.item.response.ItemContentSnapshot;
import com.example.product.entity.goods.ItemOption;
import com.example.product.entity.image.ItemImage;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemType;
import com.example.product.entity.goods.ShippingInfo;
import com.example.product.event.ItemCreatedEvent;
import com.example.product.event.ItemDeletedEvent;
import com.example.product.event.ItemUpdatedEvent;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.CategoryRepository;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemOptionRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.ShippingInfoRepository;
import com.example.product.service.content.ItemContentService;
import com.example.product.service.command.image.ItemThumbnailSyncService;
import com.example.product.service.command.image.MediaReferenceService;
import com.example.product.service.query.detail.ItemCategoryDetailResolver;
import com.example.product.service.query.detail.ItemCategoryDetailView;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductCommandService {

    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;
    private final ItemOptionRepository itemOptionRepository;
    private final ShippingInfoRepository shippingInfoRepository;
    private final ItemImageRepository itemImageRepository;
    private final ItemContentService itemContentService;
    private final MediaReferenceService mediaReferenceService;
    private final ItemThumbnailSyncService itemThumbnailSyncService;
    private final StoreOwnershipValidator storeOwnershipValidator;
    private final EventPublisher eventPublisher;
    private final ItemCategoryDetailResolver itemCategoryDetailResolver;

    public GoodsDetailResponse createProduct(ProductCreateRequest request, Long sellerId) {
        storeOwnershipValidator.validateOwnership(sellerId, request.getStoreId());
        mediaReferenceService.resolveMediaUrl(request.getThumbnailMediaId());
        validateCategoryExists(request.getCategoryId());
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
        itemContentService.replaceTags(item.getId(), request.getTags());
        itemContentService.replaceFeatures(item.getId(), request.getFeatures());
        itemContentService.replaceDetailSections(item.getId(), request.getDetailSections());

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

        log.info("Product created. itemId={}, sellerId={}, storeId={}, optionCount={}",
                item.getId(), sellerId, request.getStoreId(), options.size());

        ItemContentSnapshot contentSnapshot = itemContentService.findByItemId(item.getId());
        List<ItemImage> images = itemImageRepository.findByItemIdOrderBySortOrder(item.getId());
        ItemCategoryDetailView categoryDetail = resolveCategoryDetail(item);
        return GoodsDetailResponse.of(
                item, options, shippingInfo, List.of(), List.of(),
                categoryDetail != null ? categoryDetail.categoryName() : null,
                categoryDetail != null ? categoryDetail.categoryPath() : List.of(),
                contentSnapshot, images
        );
    }

    public GoodsDetailResponse updateProduct(Long itemId, ProductUpdateRequest request, Long sellerId) {
        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));
        item.validateOwnership(sellerId);
        validateItemType(item, ItemType.PRODUCT);
        if (!item.isEditable()) {
            throw new BusinessException(ProductErrorCode.ITEM_NOT_EDITABLE);
        }

        boolean clearThumbnail = Boolean.TRUE.equals(request.getClearThumbnail());
        Long thumbnailMediaId = request.getThumbnailMediaId();
        if (clearThumbnail && thumbnailMediaId != null) {
            throw new BusinessException(ProductErrorCode.INVALID_THUMBNAIL_UPDATE_REQUEST);
        }
        validateCategoryExists(request.getCategoryId());

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

        if (request.getOptions() != null) {
            itemOptionRepository.softDeleteAllByItemId(itemId);
            saveOptions(itemId, request.getOptions());
        }

        if (request.getShippingInfo() != null) {
            upsertShippingInfo(itemId, request.getShippingInfo());
        }
        if (request.getTags() != null) {
            itemContentService.replaceTags(itemId, request.getTags());
        }
        if (request.getFeatures() != null) {
            itemContentService.replaceFeatures(itemId, request.getFeatures());
        }
        if (request.getDetailSections() != null) {
            itemContentService.replaceDetailSections(itemId, request.getDetailSections());
        }

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
                EventMetadata.of("Item", String.valueOf(item.getId())));

        log.info("Product updated. itemId={}, sellerId={}, optionsReplaced={}, thumbnailChanged={}",
                itemId, sellerId, request.getOptions() != null, request.getThumbnailMediaId() != null || clearThumbnail);

        List<ItemOption> currentOptions = itemOptionRepository.findByItemId(itemId);
        ShippingInfo currentShippingInfo = shippingInfoRepository.findByItemId(itemId).orElse(null);
        ItemContentSnapshot contentSnapshot = itemContentService.findByItemId(itemId);
        List<ItemImage> images = itemImageRepository.findByItemIdOrderBySortOrder(itemId);
        ItemCategoryDetailView categoryDetail = resolveCategoryDetail(item);
        return GoodsDetailResponse.of(
                item, currentOptions, currentShippingInfo, List.of(), List.of(),
                categoryDetail != null ? categoryDetail.categoryName() : null,
                categoryDetail != null ? categoryDetail.categoryPath() : List.of(),
                contentSnapshot, images
        );
    }

    public void delete(Long itemId, Long sellerId) {
        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));
        item.validateOwnership(sellerId);
        validateItemType(item, ItemType.PRODUCT);
        if (!item.isDeletable()) {
            throw new BusinessException(ProductErrorCode.ITEM_NOT_DELETABLE);
        }
        item.softDelete();

        itemOptionRepository.softDeleteAllByItemId(itemId);
        shippingInfoRepository.softDeleteByItemId(itemId);
        itemContentService.softDeleteAll(itemId);
        itemImageRepository.softDeleteAllByItemId(itemId);
        item.clearThumbnail();
        itemThumbnailSyncService.syncAfterCommit(itemId, null, true);
        eventPublisher.publish(
                new ItemDeletedEvent(
                        item.getId(),
                        item.getItemType().name(),
                        item.getStatus().name(),
                        item.getSellerId(),
                        item.getStoreId()
                ),
                EventMetadata.of("Item", String.valueOf(item.getId())));

        log.info("Product deleted. itemId={}, sellerId={}", itemId, sellerId);
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
                request.getFreeShippingThreshold(), request.getEstimatedDays(), request.getReturnPolicy(),
                request.getCarrier(), request.getShipFrom(), request.getReturnAddress(),
                request.getReturnShippingFee(), request.getExchangeShippingFee(), request.getShippingNotice());
        return shippingInfoRepository.save(si);
    }

    private ShippingInfo upsertShippingInfo(Long itemId, ShippingInfoRequest request) {
        return shippingInfoRepository.findByItemId(itemId)
                .map(existing -> {
                    existing.update(
                            request.getShippingFee(),
                            request.getFreeShippingThreshold(),
                            request.getEstimatedDays(),
                            request.getReturnPolicy(),
                            request.getCarrier(),
                            request.getShipFrom(),
                            request.getReturnAddress(),
                            request.getReturnShippingFee(),
                            request.getExchangeShippingFee(),
                            request.getShippingNotice()
                    );
                    return existing;
                })
                .orElseGet(() -> saveShippingInfo(itemId, request));
    }

    private ItemCategoryDetailView resolveCategoryDetail(Item item) {
        Map<Long, ItemCategoryDetailView> resolved = itemCategoryDetailResolver != null
                ? itemCategoryDetailResolver.resolve(List.of(item))
                : Map.of();
        if (resolved == null) {
            return null;
        }
        return resolved.get(item.getId());
    }

    private void validateItemType(Item item, ItemType expectedType) {
        if (item.getItemType() != expectedType) {
            throw new BusinessException(ProductErrorCode.ITEM_TYPE_MISMATCH);
        }
    }

    private void validateCategoryExists(Long categoryId) {
        if (categoryId != null && !categoryRepository.existsById(categoryId)) {
            throw new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }
    }

}
