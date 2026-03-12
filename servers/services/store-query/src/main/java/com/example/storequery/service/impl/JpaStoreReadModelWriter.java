package com.example.storequery.service.impl;

import com.example.storequery.entity.StoreReadImage;
import com.example.storequery.entity.StoreReadItem;
import com.example.storequery.entity.StoreReadModel;
import com.example.storequery.projection.StoreReadImageSnapshot;
import com.example.storequery.projection.StoreReadItemSnapshot;
import com.example.storequery.projection.StoreReadModelSnapshot;
import com.example.storequery.repository.StoreReadImageRepository;
import com.example.storequery.repository.StoreReadItemRepository;
import com.example.storequery.repository.StoreReadModelRepository;
import com.example.storequery.service.StoreReadModelWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class JpaStoreReadModelWriter implements StoreReadModelWriter {

    private final StoreReadModelRepository storeReadModelRepository;
    private final StoreReadImageRepository storeReadImageRepository;
    private final StoreReadItemRepository storeReadItemRepository;

    @Override
    public void upsert(StoreReadModelSnapshot snapshot) {
        LocalDateTime projectedAt = LocalDateTime.now();

        StoreReadModel readModel = storeReadModelRepository.findById(snapshot.storeId())
            .map(existing -> {
                existing.restoreForProjection();
                existing.project(
                    snapshot.userId(),
                    snapshot.storeName(),
                    snapshot.ownerNickname(),
                    snapshot.ownerProfileImageUrl(),
                    snapshot.status(),
                    snapshot.description(),
                    snapshot.defaultAddress(),
                    snapshot.defaultAddressType(),
                    snapshot.primaryContactValue(),
                    snapshot.primaryContactType(),
                    snapshot.thumbnailMediaId(),
                    snapshot.thumbnailUrl(),
                    snapshot.thumbnailSortOrder(),
                    snapshot.galleryCount(),
                    snapshot.activeItemCount(),
                    snapshot.latestItemUpdatedAt(),
                    snapshot.searchText(),
                    snapshot.sourceCreatedAt(),
                    snapshot.sourceUpdatedAt(),
                    projectedAt
                );
                return existing;
            })
            .orElseGet(() -> StoreReadModel.of(
                snapshot.storeId(),
                snapshot.userId(),
                snapshot.storeName(),
                snapshot.ownerNickname(),
                snapshot.ownerProfileImageUrl(),
                snapshot.status(),
                snapshot.description(),
                snapshot.defaultAddress(),
                snapshot.defaultAddressType(),
                snapshot.primaryContactValue(),
                snapshot.primaryContactType(),
                snapshot.thumbnailMediaId(),
                snapshot.thumbnailUrl(),
                snapshot.thumbnailSortOrder(),
                snapshot.galleryCount(),
                snapshot.activeItemCount(),
                snapshot.latestItemUpdatedAt(),
                snapshot.searchText(),
                snapshot.sourceCreatedAt(),
                snapshot.sourceUpdatedAt(),
                projectedAt
            ));

        storeReadModelRepository.save(readModel);

        replaceImages(snapshot.storeId(), snapshot.images(), projectedAt);
        replaceItems(snapshot.storeId(), snapshot.items(), projectedAt);
    }

    @Override
    public void softDeleteByStoreId(Long storeId) {
        if (storeId == null || storeId <= 0L) {
            throw new IllegalArgumentException("storeId must be positive");
        }

        storeReadModelRepository.findById(storeId).ifPresent(model -> {
            if (!model.isDeleted()) {
                model.softDelete();
                storeReadModelRepository.save(model);
            }
        });
        storeReadImageRepository.softDeleteActiveByStoreId(storeId);
        storeReadItemRepository.softDeleteActiveByStoreId(storeId);
    }

    private void replaceImages(Long storeId, List<StoreReadImageSnapshot> imageSnapshots, LocalDateTime projectedAt) {
        storeReadImageRepository.softDeleteActiveByStoreId(storeId);

        if (imageSnapshots == null || imageSnapshots.isEmpty()) {
            return;
        }

        List<StoreReadImage> images = imageSnapshots.stream()
            .map(image -> StoreReadImage.of(
                storeId,
                image.imageType(),
                image.mediaId(),
                image.mediaUrl(),
                image.sortOrder(),
                image.sourceUpdatedAt(),
                projectedAt
            ))
            .toList();
        storeReadImageRepository.saveAll(images);
    }

    private void replaceItems(Long storeId, List<StoreReadItemSnapshot> itemSnapshots, LocalDateTime projectedAt) {
        storeReadItemRepository.softDeleteActiveByStoreId(storeId);

        if (itemSnapshots == null || itemSnapshots.isEmpty()) {
            return;
        }

        List<StoreReadItem> items = itemSnapshots.stream()
            .map(item -> StoreReadItem.of(
                item.itemId(),
                item.storeId(),
                item.sellerId(),
                item.title(),
                item.price(),
                item.itemType(),
                item.status(),
                item.thumbnailMediaId(),
                item.thumbnailUrl(),
                item.sourceUpdatedAt(),
                projectedAt
            ))
            .toList();
        storeReadItemRepository.saveAll(items);
    }
}
