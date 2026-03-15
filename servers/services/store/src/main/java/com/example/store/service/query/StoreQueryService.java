package com.example.store.service.query;

import com.example.core.exception.BusinessException;
import com.example.core.exception.CommonErrorCode;
import com.example.core.exception.TechnicalException;
import com.example.store.dto.image.StoreImageResponse;
import com.example.store.dto.response.StoreDetailResponse;
import com.example.store.dto.response.StoreListResponse;
import com.example.store.dto.response.internal.StoreSnapshotResponse;
import com.example.store.exception.StoreErrorCode;
import com.example.store.repository.StoresRepository;
import com.example.store.service.query.view.StoreDetailBaseView;
import com.example.store.service.query.view.StoreImageView;
import com.example.store.service.query.view.StoreListView;
import com.example.store.service.query.view.StoreSnapshotBaseView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.store.exception.StoreErrorCode.STORE_NOT_FOUND;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreQueryService {

    private final StoresRepository storesRepository;
    private final StoreDetailAssembler storeDetailAssembler;

    public Page<StoreListResponse>  getStoreListInfo(Pageable pageable){
        try {
            return storesRepository.findStoreList(pageable)
                .map(this::toStoreListResponse);
        } catch (BusinessException e){
            throw new BusinessException(STORE_NOT_FOUND);
        } catch (TechnicalException e){
            throw new TechnicalException(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    public List<StoreListResponse> getMyStoreList(Long userId) {
        if (userId == null || userId <= 0L) {
            return List.of();
        }

        return storesRepository.findStoreListByUserId(userId).stream()
            .map(this::toStoreListResponse)
            .toList();
    }

    public StoreDetailResponse getStoreDetail(Long storeId){
        StoreDetailBaseView base = storesRepository.findDetailBaseByStoreId(storeId)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        List<StoreImageView> images = storesRepository.findImagesByStoreId(storeId);

        return storeDetailAssembler.toDetailResponse(base, images);
    }

    public StoreSnapshotResponse getStoreSnapshot(Long storeId) {
        StoreSnapshotBaseView base = storesRepository.findSnapshotBaseByStoreId(storeId)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        return storeDetailAssembler.toSnapshotResponse(base, storesRepository.findImagesByStoreId(storeId));
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

    private StoreListResponse toStoreListResponse(StoreListView view) {
        StoreImageResponse thumbnail = null;
        if (view.thumbnailMediaId() != null || view.thumbnailImageType() != null || view.thumbnailSortOrder() != null) {
            thumbnail = StoreImageResponse.builder()
                .storeId(view.id())
                .mediaId(view.thumbnailMediaId())
                .imageType(view.thumbnailImageType())
                .sortOrder(view.thumbnailSortOrder())
                .build();
        }

        return new StoreListResponse(
            view.id(),
            view.userId(),
            view.storeName(),
            view.status(),
            view.description(),
            view.contactValue(),
            view.address(),
            thumbnail
        );
    }

}
