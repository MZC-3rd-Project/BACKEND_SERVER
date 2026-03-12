package com.example.storequery.service.query;

import com.example.core.exception.BusinessException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    public Page<StoreQueryListResponse> getStores(String keyword, StoreQueryStatus status, Pageable pageable) {
        Page<StoreReadModel> page;
        if (StringUtils.hasText(keyword)) {
            page = storeReadModelRepository.search(keyword.trim(), status == null ? null : status.name(), SEARCH_SIMILARITY_THRESHOLD, pageable);
        } else if (status != null) {
            page = storeReadModelRepository.findByDeletedAtIsNullAndStatusOrderBySourceUpdatedAtDesc(status, pageable);
        } else {
            page = storeReadModelRepository.findByDeletedAtIsNullOrderBySourceUpdatedAtDesc(pageable);
        }

        return page.map(StoreQueryListResponse::from);
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
}
