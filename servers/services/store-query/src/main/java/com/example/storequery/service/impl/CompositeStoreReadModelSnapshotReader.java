package com.example.storequery.service.impl;

import com.example.storequery.entity.StoreQueryImageType;
import com.example.storequery.projection.StoreReadImageSnapshot;
import com.example.storequery.projection.StoreReadItemSnapshot;
import com.example.storequery.projection.StoreReadModelSnapshot;
import com.example.storequery.service.StoreReadModelSnapshotReader;
import com.example.storequery.source.StoreItemSummarySource;
import com.example.storequery.source.StoreItemSummarySourceReader;
import com.example.storequery.source.StoreOwnerSnapshot;
import com.example.storequery.source.StoreOwnerSnapshotSourceReader;
import com.example.storequery.source.StoreSourceSnapshot;
import com.example.storequery.source.StoreSourceSnapshotReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CompositeStoreReadModelSnapshotReader implements StoreReadModelSnapshotReader {

    private static final Set<String> INACTIVE_ITEM_STATUSES = Set.of(
        "DRAFT",
        "HIDDEN",
        "CLOSED",
        "FUND_FAILED"
    );

    private final StoreSourceSnapshotReader storeSourceSnapshotReader;
    private final StoreOwnerSnapshotSourceReader ownerSnapshotSourceReader;
    private final StoreItemSummarySourceReader itemSummarySourceReader;

    @Override
    public Optional<StoreReadModelSnapshot> read(Long storeId) {
        if (storeId == null || storeId <= 0L) {
            return Optional.empty();
        }

        Optional<StoreSourceSnapshot> storeSource = storeSourceSnapshotReader.read(storeId);
        if (storeSource.isEmpty()) {
            return Optional.empty();
        }

        StoreSourceSnapshot store = storeSource.get();
        StoreOwnerSnapshot owner = ownerSnapshotSourceReader.readByUserId(store.userId()).orElse(null);
        List<StoreItemSummarySource> itemSources = itemSummarySourceReader.readByStoreId(storeId);
        List<StoreReadItemSnapshot> itemSnapshots = itemSources.stream()
            .map(this::toItemSnapshot)
            .toList();

        StoreReadImageSnapshot selectedThumbnail = selectThumbnail(store.images());
        int galleryCount = calculateGalleryCount(store.images(), selectedThumbnail);

        return Optional.of(new StoreReadModelSnapshot(
            store.storeId(),
            store.userId(),
            store.storeName(),
            owner == null ? null : owner.nickname(),
            owner == null ? null : owner.profileImageUrl(),
            store.status(),
            store.description(),
            store.defaultAddress(),
            store.defaultAddressType(),
            store.primaryContactValue(),
            store.primaryContactType(),
            selectedThumbnail == null ? null : selectedThumbnail.mediaId(),
            selectedThumbnail == null ? null : selectedThumbnail.mediaUrl(),
            selectedThumbnail == null ? null : selectedThumbnail.sortOrder(),
            galleryCount,
            countActiveItems(itemSnapshots),
            latestItemUpdatedAt(itemSnapshots),
            buildSearchText(store, owner, itemSnapshots),
            store.sourceCreatedAt(),
            store.sourceUpdatedAt(),
            normalizeImages(store.images()),
            itemSnapshots
        ));
    }

    private StoreReadItemSnapshot toItemSnapshot(StoreItemSummarySource source) {
        return new StoreReadItemSnapshot(
            source.itemId(),
            source.storeId(),
            source.sellerId(),
            source.title(),
            source.price(),
            source.itemType(),
            source.status(),
            source.thumbnailMediaId(),
            source.thumbnailUrl(),
            source.sourceUpdatedAt()
        );
    }

    private List<StoreReadImageSnapshot> normalizeImages(List<StoreReadImageSnapshot> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        return images.stream()
            .sorted(Comparator
                .comparing(StoreReadImageSnapshot::sortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(StoreReadImageSnapshot::mediaId, Comparator.nullsLast(Long::compareTo)))
            .toList();
    }

    private StoreReadImageSnapshot selectThumbnail(List<StoreReadImageSnapshot> images) {
        List<StoreReadImageSnapshot> normalized = normalizeImages(images);
        return normalized.stream()
            .filter(image -> image.imageType() == StoreQueryImageType.THUMBNAIL)
            .findFirst()
            .or(() -> normalized.stream().findFirst())
            .orElse(null);
    }

    private int calculateGalleryCount(List<StoreReadImageSnapshot> images, StoreReadImageSnapshot selectedThumbnail) {
        List<StoreReadImageSnapshot> normalized = normalizeImages(images);
        if (normalized.isEmpty()) {
            return 0;
        }
        if (selectedThumbnail == null) {
            return normalized.size();
        }
        return (int) normalized.stream()
            .filter(image -> !image.mediaId().equals(selectedThumbnail.mediaId()))
            .count();
    }

    private LocalDateTime latestItemUpdatedAt(List<StoreReadItemSnapshot> itemSnapshots) {
        return itemSnapshots.stream()
            .map(StoreReadItemSnapshot::sourceUpdatedAt)
            .max(LocalDateTime::compareTo)
            .orElse(null);
    }

    private int countActiveItems(List<StoreReadItemSnapshot> itemSnapshots) {
        return (int) itemSnapshots.stream()
            .filter(this::isActiveItem)
            .count();
    }

    private String buildSearchText(
        StoreSourceSnapshot store,
        StoreOwnerSnapshot owner,
        List<StoreReadItemSnapshot> itemSnapshots
    ) {
        Set<String> terms = new LinkedHashSet<>();
        addSearchTerm(terms, store.storeName());
        addSearchTerm(terms, store.description());
        addSearchTerm(terms, store.defaultAddress());
        if (owner != null) {
            addSearchTerm(terms, owner.nickname());
        }
        itemSnapshots.stream()
            .filter(this::isActiveItem)
            .map(StoreReadItemSnapshot::title)
            .forEach(title -> addSearchTerm(terms, title));
        return String.join(" ", terms);
    }

    private boolean isActiveItem(StoreReadItemSnapshot snapshot) {
        String status = snapshot.status();
        return status != null && !INACTIVE_ITEM_STATUSES.contains(status.trim().toUpperCase(Locale.ROOT));
    }

    private void addSearchTerm(Set<String> terms, String value) {
        String normalized = safe(value).trim();
        if (!normalized.isEmpty()) {
            terms.add(normalized);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
