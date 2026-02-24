package com.example.product.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.product.dto.image.request.ItemImageRequest;
import com.example.product.dto.image.response.ItemImageResponse;
import com.example.product.entity.image.ItemImage;
import com.example.product.entity.item.Item;
import com.example.product.event.ItemUpdatedEvent;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ItemImageCommandService {

    private final ItemImageRepository itemImageRepository;
    private final ItemRepository itemRepository;
    private final MediaReferenceService mediaReferenceService;
    private final ItemThumbnailSyncService itemThumbnailSyncService;
    private final EventPublisher eventPublisher;

    public List<ItemImageResponse> addImages(Long itemId, List<ItemImageRequest> requests, Long userId) {
        Item item = validateOwnership(itemId, userId);
        if (requests == null || requests.isEmpty()) {
            return toResponses(normalizeImages(item).images());
        }

        validateDuplicateMediaIds(requests);
        mediaReferenceService.validateMediaReferences(
                requests.stream().map(ItemImageRequest::getMediaId).toList());
        itemImageRepository.saveAll(requests.stream()
                .map(req -> ItemImage.create(
                        itemId,
                        req.getMediaId(),
                        req.getSortOrder(),
                        req.isThumbnail()
                ))
                .toList());

        NormalizedImages normalized = normalizeImages(item);
        syncItemMediaLinks(item.getId(), normalized);
        publishItemUpdated(item);
        return toResponses(normalized.images());
    }

    public void deleteImage(Long imageId, Long userId) {
        ItemImage image = itemImageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.IMAGE_NOT_FOUND));
        Item item = validateOwnership(image.getItemId(), userId);
        image.softDelete();
        NormalizedImages normalized = normalizeImages(item);
        syncItemMediaLinks(item.getId(), normalized);
        publishItemUpdated(item);
    }

    public List<ItemImageResponse> reorder(Long itemId, List<Long> imageIds, Long userId) {
        Item item = validateOwnership(itemId, userId);
        List<ItemImage> images = itemImageRepository.findByItemIdOrderBySortOrder(itemId);
        validateReorderRequest(images, imageIds);
        Map<Long, ItemImage> imageById = images.stream()
                .collect(Collectors.toMap(ItemImage::getId, Function.identity()));

        // Keep the unique(item_id, sort_order) constraint stable during reordering.
        // We first move all rows to a temporary disjoint range and flush, then write final order.
        int temporaryOrderBase = imageIds.size() + 10_000;
        applySortOrder(imageById, imageIds, temporaryOrderBase);
        itemImageRepository.flush();

        applySortOrder(imageById, imageIds, 0);

        NormalizedImages normalized = normalizeImages(item);
        syncItemMediaLinks(item.getId(), normalized);
        publishItemUpdated(item);
        return toResponses(normalized.images());
    }

    private Item validateOwnership(Long itemId, Long userId) {
        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));
        item.validateOwnership(userId);
        return item;
    }

    private NormalizedImages normalizeImages(Item item) {
        List<ItemImage> images = itemImageRepository.findByItemIdOrderBySortOrder(item.getId());
        if (images.isEmpty()) {
            item.clearThumbnail();
            return new NormalizedImages(List.of(), null, List.of());
        }

        List<ItemImage> sorted = images.stream()
                .sorted(Comparator
                        .comparing(ItemImage::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ItemImage::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).updateSortOrder(i);
        }

        ItemImage thumbnail = sorted.stream()
                .filter(image -> Boolean.TRUE.equals(image.getIsThumbnail()))
                .findFirst()
                .orElse(sorted.get(0));

        for (ItemImage image : sorted) {
            image.setThumbnail(image.getId().equals(thumbnail.getId()));
        }
        Long thumbnailMediaId = thumbnail.getMediaId();
        List<Long> galleryMediaIds = sorted.stream()
                .filter(image -> !image.getId().equals(thumbnail.getId()))
                .map(ItemImage::getMediaId)
                .filter(Objects::nonNull)
                .toList();
        item.updateThumbnail(thumbnailMediaId);

        return new NormalizedImages(sorted, thumbnailMediaId, galleryMediaIds);
    }

    private void syncItemMediaLinks(Long itemId, NormalizedImages normalizedImages) {
        itemThumbnailSyncService.syncAfterCommit(
                itemId,
                normalizedImages.thumbnailMediaId(),
                normalizedImages.galleryMediaIds()
        );
    }

    private void applySortOrder(Map<Long, ItemImage> imageById, List<Long> imageIds, int orderOffset) {
        for (int i = 0; i < imageIds.size(); i++) {
            Long targetId = imageIds.get(i);
            ItemImage image = imageById.get(targetId);
            if (image == null) {
                throw new BusinessException(ProductErrorCode.INVALID_IMAGE_REORDER_REQUEST);
            }
            image.updateSortOrder(orderOffset + i);
        }
    }

    private List<ItemImageResponse> toResponses(List<ItemImage> images) {
        return images.stream()
                .map(ItemImageResponse::from)
                .toList();
    }

    private void validateDuplicateMediaIds(List<ItemImageRequest> requests) {
        Set<Long> seen = new HashSet<>();
        for (ItemImageRequest request : requests) {
            Long mediaId = request.getMediaId();
            if (mediaId == null || !seen.add(mediaId)) {
                throw new BusinessException(ProductErrorCode.INVALID_IMAGE_REORDER_REQUEST);
            }
        }
    }

    private void validateReorderRequest(List<ItemImage> images, List<Long> imageIds) {
        if (imageIds == null || imageIds.size() != images.size()) {
            throw new BusinessException(ProductErrorCode.INVALID_IMAGE_REORDER_REQUEST);
        }
        Set<Long> uniqueIds = new HashSet<>(imageIds);
        if (uniqueIds.size() != imageIds.size()) {
            throw new BusinessException(ProductErrorCode.INVALID_IMAGE_REORDER_REQUEST);
        }
    }

    private void publishItemUpdated(Item item) {
        eventPublisher.publish(
                new ItemUpdatedEvent(
                        item.getId(),
                        item.getTitle(),
                        item.getPrice(),
                        item.getThumbnailMediaId(),
                        System.currentTimeMillis()
                ),
                EventMetadata.of("Item", String.valueOf(item.getId()))
        );
    }

    private record NormalizedImages(
            List<ItemImage> images,
            Long thumbnailMediaId,
            List<Long> galleryMediaIds
    ) {
    }
}
