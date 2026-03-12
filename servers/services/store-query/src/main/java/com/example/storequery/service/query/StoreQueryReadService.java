package com.example.storequery.service.query;

import com.example.core.exception.BusinessException;
import com.example.core.pagination.CursorRequest;
import com.example.core.pagination.CursorResponse;
import com.example.storequery.dto.response.StoreQueryDetailResponse;
import com.example.storequery.dto.response.StoreQueryImageResponse;
import com.example.storequery.dto.response.StoreQueryImagesResponse;
import com.example.storequery.dto.response.StoreQueryItemSummaryResponse;
import com.example.storequery.dto.response.StoreQueryListResponse;
import com.example.storequery.entity.StoreQueryImageType;
import com.example.storequery.entity.StoreQueryStatus;
import com.example.storequery.entity.StoreReadImage;
import com.example.storequery.entity.StoreReadItem;
import com.example.storequery.entity.StoreReadModel;
import com.example.storequery.exception.StoreQueryErrorCode;
import com.example.storequery.repository.StoreReadImageRepository;
import com.example.storequery.repository.StoreReadItemRepository;
import com.example.storequery.repository.StoreReadModelRepository;
import com.example.storequery.repository.StoreReadModelSearchRow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreQueryReadService {

    private static final double SEARCH_SIMILARITY_THRESHOLD = 0.2d;

    private final StoreReadModelRepository storeReadModelRepository;
    private final StoreReadImageRepository storeReadImageRepository;
    private final StoreReadItemRepository storeReadItemRepository;

    public CursorResponse<StoreQueryListResponse> getStores(String keyword, StoreQueryStatus status, String cursor, int size) {
        CursorRequest request = CursorRequest.of(cursor, size);
        if (StringUtils.hasText(keyword)) {
            return searchStores(keyword.trim(), status, request);
        }

        return findStores(status, request);
    }

    public StoreQueryDetailResponse getStoreDetail(Long storeId) {
        StoreReadModel model = storeReadModelRepository.findByStoreIdAndDeletedAtIsNull(storeId)
            .orElseThrow(() -> new BusinessException(StoreQueryErrorCode.STORE_READ_MODEL_NOT_FOUND));

        List<StoreReadImage> images = storeReadImageRepository.findByStoreIdAndDeletedAtIsNullOrderBySortOrderAsc(storeId);
        List<StoreReadItem> items = storeReadItemRepository.findByStoreIdAndDeletedAtIsNullOrderBySourceUpdatedAtDesc(storeId);

        StoreQueryImageResponse thumbnail = toThumbnail(model, images);
        List<StoreQueryImageResponse> gallery = images.stream()
            .sorted(Comparator.comparing(StoreReadImage::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
            .filter(image -> thumbnail == null || !Objects.equals(image.getMediaId(), thumbnail.mediaId()))
            .map(StoreQueryImageResponse::of)
            .toList();

        return StoreQueryDetailResponse.builder()
            .storeId(model.getStoreId())
            .userId(model.getUserId())
            .storeName(model.getStoreName())
            .status(model.getStatus())
            .description(model.getDescription())
            .address(model.getDefaultAddress())
            .addressType(model.getDefaultAddressType())
            .contactValue(model.getPrimaryContactValue())
            .contactType(model.getPrimaryContactType())
            .ownerNickname(model.getOwnerNickname())
            .ownerProfileImageUrl(model.getOwnerProfileImageUrl())
            .activeItemCount(model.getActiveItemCount())
            .latestItemUpdatedAt(model.getLatestItemUpdatedAt())
            .images(StoreQueryImagesResponse.builder()
                .thumbnail(thumbnail)
                .gallery(gallery)
                .build())
            .items(items.stream().map(StoreQueryItemSummaryResponse::from).toList())
            .build();
    }

    public List<StoreQueryListResponse> getMyStores(Long userId) {
        return getStoresByUserId(userId);
    }

    public List<StoreQueryListResponse> getStoresByUserId(Long userId) {
        return storeReadModelRepository.findByUserIdAndDeletedAtIsNullOrderBySourceUpdatedAtDesc(userId).stream()
            .map(StoreQueryListResponse::from)
            .toList();
    }

    private CursorResponse<StoreQueryListResponse> findStores(StoreQueryStatus status, CursorRequest request) {
        StoreQueryCursorCodec.ListCursor cursor = StoreQueryCursorCodec.decodeList(request.getCursor());
        List<StoreReadModel> stores = storeReadModelRepository.findListWithCursor(
                status,
                cursor == null ? null : cursor.sourceUpdatedAt(),
                cursor == null ? null : cursor.storeId(),
                PageRequest.of(0, request.getSize() + 1)
        );

        boolean hasNext = stores.size() > request.getSize();
        List<StoreReadModel> pageItems = hasNext ? stores.subList(0, request.getSize()) : stores;
        List<StoreQueryListResponse> content = pageItems.stream()
                .map(StoreQueryListResponse::from)
                .toList();

        String nextCursor = hasNext
                ? StoreQueryCursorCodec.encodeList(
                        pageItems.get(pageItems.size() - 1).getSourceUpdatedAt(),
                        pageItems.get(pageItems.size() - 1).getStoreId())
                : null;

        return CursorResponse.of(content, nextCursor);
    }

    private CursorResponse<StoreQueryListResponse> searchStores(String keyword, StoreQueryStatus status, CursorRequest request) {
        StoreQueryCursorCodec.SearchCursor cursor = StoreQueryCursorCodec.decodeSearch(request.getCursor());
        List<StoreReadModelSearchRow> stores = storeReadModelRepository.searchWithCursor(
                keyword,
                status == null ? null : status.name(),
                SEARCH_SIMILARITY_THRESHOLD,
                cursor == null ? null : cursor.sortRank(),
                cursor == null ? null : cursor.sourceUpdatedAt(),
                cursor == null ? null : cursor.storeId(),
                request.getSize() + 1
        );

        boolean hasNext = stores.size() > request.getSize();
        List<StoreReadModelSearchRow> pageItems = hasNext ? stores.subList(0, request.getSize()) : stores;
        List<StoreQueryListResponse> content = pageItems.stream()
                .map(this::toListResponse)
                .toList();

        String nextCursor = hasNext
                ? StoreQueryCursorCodec.encodeSearch(
                        pageItems.get(pageItems.size() - 1).getSortRank(),
                        pageItems.get(pageItems.size() - 1).getSourceUpdatedAt(),
                        pageItems.get(pageItems.size() - 1).getStoreId())
                : null;

        return CursorResponse.of(content, nextCursor);
    }

    private StoreQueryImageResponse toThumbnail(StoreReadModel model, List<StoreReadImage> images) {
        if (model.getThumbnailMediaId() != null) {
            return StoreQueryImageResponse.of(
                model.getThumbnailMediaId(),
                model.getThumbnailUrl(),
                StoreQueryImageType.THUMBNAIL,
                model.getThumbnailSortOrder()
            );
        }

        return images.stream()
            .filter(image -> image.getImageType() == StoreQueryImageType.THUMBNAIL)
            .min(Comparator.comparing(StoreReadImage::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
            .map(StoreQueryImageResponse::of)
            .orElse(null);
    }

    private StoreQueryListResponse toListResponse(StoreReadModelSearchRow row) {
        return StoreQueryListResponse.builder()
                .storeId(row.getStoreId())
                .userId(row.getUserId())
                .storeName(row.getStoreName())
                .status(StoreQueryStatus.valueOf(row.getStatus()))
                .description(row.getDescription())
                .contactValue(row.getPrimaryContactValue())
                .address(row.getDefaultAddress())
                .ownerNickname(row.getOwnerNickname())
                .thumbnail(toThumbnail(row))
                .build();
    }

    private StoreQueryImageResponse toThumbnail(StoreReadModelSearchRow row) {
        if (row.getThumbnailMediaId() == null) {
            return null;
        }

        return StoreQueryImageResponse.of(
                row.getThumbnailMediaId(),
                row.getThumbnailUrl(),
                StoreQueryImageType.THUMBNAIL,
                row.getThumbnailSortOrder()
        );
    }
}
