package com.example.store.service.query;

import com.example.core.exception.BusinessException;
import com.example.core.exception.CommonErrorCode;
import com.example.core.exception.TechnicalException;
import com.example.store.dto.image.StoreImagesResponse;
import com.example.store.dto.response.StoreDetailResponse;
import com.example.store.dto.response.StoreListResponse;
import com.example.store.dto.response.internal.StoreSnapshotResponse;
import com.example.store.entity.StoreImage;
import com.example.store.exception.StoreErrorCode;
import com.example.store.repository.StoresRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static com.example.store.exception.StoreErrorCode.STORE_NOT_FOUND;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreQueryService {

    private final StoresRepository storesRepository;

    public Page<StoreListResponse>  getStoreListInfo(Pageable pageable){
        try {
            return storesRepository.findStoreList(pageable);
        } catch (BusinessException e){
            throw new BusinessException(STORE_NOT_FOUND);
        } catch (TechnicalException e){
            throw new TechnicalException(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    public StoreDetailResponse getStoreDetail(Long storeId){
        // 1. 기본 정보 조회
        StoreDetailResponse base = storesRepository.findByStoreId(storeId)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        // 2. 이미지 조회
        List<StoreImage> images = storesRepository.findImagesByStoreId(storeId);

        // 3. 이미지 조합 (썸네일 자동 선택)
        StoreImagesResponse imagesResponse = StoreImagesResponse.from(images);

        // 4. 합쳐서 반환
        return base.from(imagesResponse);
    }

    public StoreSnapshotResponse getStoreSnapshot(Long storeId) {
        StoreSnapshotResponse base = storesRepository.findSnapshotByStoreId(storeId)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        List<StoreSnapshotResponse.StoreSnapshotImageResponse> images = storesRepository.findImagesByStoreId(storeId).stream()
            .map(StoreSnapshotResponse.StoreSnapshotImageResponse::from)
            .toList();

        LocalDateTime sourceUpdatedAt = images.stream()
            .map(StoreSnapshotResponse.StoreSnapshotImageResponse::sourceUpdatedAt)
            .filter(Objects::nonNull)
            .max(LocalDateTime::compareTo)
            .map(updatedAt -> {
                if (base.sourceUpdatedAt() == null) {
                    return updatedAt;
                }
                return updatedAt.isAfter(base.sourceUpdatedAt()) ? updatedAt : base.sourceUpdatedAt();
            })
            .orElse(base.sourceUpdatedAt());

        return base.withImages(images).withSourceUpdatedAt(sourceUpdatedAt);
    }

    public List<Long> getActiveStoreIdsByUserId(Long userId) {
        if (userId == null || userId <= 0L) {
            return List.of();
        }
        return storesRepository.findIdsByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId);
    }

    public List<Long> getStoreIdsByUserId(Long userId) {
        return getActiveStoreIdsByUserId(userId);
    }

}
