package com.example.storequery.service.query;

import com.example.core.exception.BusinessException;
import com.example.core.pagination.CursorRequest;
import com.example.core.pagination.CursorResponse;
import com.example.storequery.dto.response.StoreQueryDetailResponse;
import com.example.storequery.dto.response.StoreQueryListResponse;
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

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreQueryReadService {

    private static final double SEARCH_SIMILARITY_THRESHOLD = 0.2d;

    private final StoreReadModelRepository storeReadModelRepository;
    private final StoreReadImageRepository storeReadImageRepository;
    private final StoreReadItemRepository storeReadItemRepository;
    private final StoreSummaryAssembler storeSummaryAssembler;
    private final StoreDetailAssembler storeDetailAssembler;

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
        return storeDetailAssembler.toDetailResponse(model, images, items);
    }

    public List<StoreQueryListResponse> getMyStores(Long userId) {
        return getStoresByUserId(userId);
    }

    public List<StoreQueryListResponse> getStoresByUserId(Long userId) {
        return storeReadModelRepository.findByUserIdAndDeletedAtIsNullOrderBySourceUpdatedAtDesc(userId).stream()
            .map(StoreReadModelSummarySource::new)
            .map(storeSummaryAssembler::toListResponse)
            .toList();
    }

    private CursorResponse<StoreQueryListResponse> findStores(StoreQueryStatus status, CursorRequest request) {
        StoreQueryCursorCodec.ListCursor cursor = StoreQueryCursorCodec.decodeList(request.getCursor());
        List<StoreReadModel> stores = cursor == null
                ? storeReadModelRepository.findListFirstPage(
                        status,
                        PageRequest.of(0, request.getSize() + 1)
                )
                : storeReadModelRepository.findListWithCursor(
                        status,
                        cursor.sourceUpdatedAt(),
                        cursor.storeId(),
                        PageRequest.of(0, request.getSize() + 1)
                );

        boolean hasNext = stores.size() > request.getSize();
        List<StoreReadModel> pageItems = hasNext ? stores.subList(0, request.getSize()) : stores;
        List<StoreQueryListResponse> content = pageItems.stream()
                .map(StoreReadModelSummarySource::new)
                .map(storeSummaryAssembler::toListResponse)
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
                .map(StoreSearchRowSummarySource::new)
                .map(storeSummaryAssembler::toListResponse)
                .toList();

        String nextCursor = hasNext
                ? StoreQueryCursorCodec.encodeSearch(
                        pageItems.get(pageItems.size() - 1).getSortRank(),
                        pageItems.get(pageItems.size() - 1).getSourceUpdatedAt(),
                        pageItems.get(pageItems.size() - 1).getStoreId())
                : null;

        return CursorResponse.of(content, nextCursor);
    }
}
