package com.example.sales.service.query;

import com.example.clients.product.exception.ProductClientException;
import com.example.clients.product.facade.ProductItemQueryClientFacade;
import com.example.clients.stock.exception.StockClientException;
import com.example.clients.stock.facade.StockAvailabilityQueryFacade;
import com.example.core.exception.BusinessException;
import com.example.core.pagination.CursorResponse;
import com.example.core.pagination.CursorUtils;
import com.example.sales.dto.response.SalesProductDetailResponse;
import com.example.sales.dto.response.SalesProductListItemResponse;
import com.example.sales.entity.PurchaseStatus;
import com.example.sales.exception.SalesErrorCode;
import com.example.sales.repository.PurchaseRepository;
import com.example.sales.repository.SalesItemCursorProjection;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesProductQueryService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final List<PurchaseStatus> SOLD_COUNT_STATUSES = List.of(
            PurchaseStatus.CONFIRMED,
            PurchaseStatus.COMPLETED
    );

    private final PurchaseRepository purchaseRepository;
    private final ProductItemQueryClientFacade productItemQueryClientFacade;
    private final StockAvailabilityQueryFacade stockAvailabilityQueryFacade;

    public CursorResponse<SalesProductListItemResponse> findProducts(String cursor, Integer size) {
        int normalizedSize = normalizeSize(size);
        Long cursorId = decodeCursor(cursor);

        List<SalesItemCursorProjection> rows = purchaseRepository.findSalesItemCursorPage(
                SOLD_COUNT_STATUSES,
                cursorId,
                PageRequest.of(0, normalizedSize + 1)
        );

        boolean hasNext = rows.size() > normalizedSize;
        List<SalesItemCursorProjection> pageRows = hasNext
                ? rows.subList(0, normalizedSize)
                : rows;

        List<SalesProductListItemResponse> items = new ArrayList<>();
        for (SalesItemCursorProjection row : pageRows) {
            SalesProductListItemResponse item = buildListItem(row.getItemId());
            if (item != null) {
                items.add(item);
            }
        }

        String nextCursor = hasNext && !pageRows.isEmpty()
                ? CursorUtils.encode(pageRows.get(pageRows.size() - 1).getCursorId())
                : null;

        long totalCount = purchaseRepository.countDistinctItemIdByStatusIn(SOLD_COUNT_STATUSES);
        return CursorResponse.of(items, nextCursor, totalCount);
    }

    public SalesProductDetailResponse findProductDetail(Long saleId) {
        if (saleId == null || saleId <= 0) {
            throw new BusinessException(SalesErrorCode.INVALID_REQUEST, "saleId는 양수여야 합니다.");
        }

        JsonNode product = fetchProduct(saleId);
        if (product == null) {
            throw new BusinessException(SalesErrorCode.PURCHASE_NOT_FOUND);
        }

        int stockLeft = fetchStockLeft(saleId);
        long soldCount = purchaseRepository.countByItemIdAndStatusIn(saleId, SOLD_COUNT_STATUSES);

        return SalesProductDetailResponse.builder()
                .id(saleId)
                .title(textOrNull(product.path("title")))
                .fundingTitle(null)
                .storeName(null)
                .category(null)
                .thumbnailUrl(null)
                .thumbnailMediaId(asNullableLong(product.path("images").path("thumbnail").path("mediaId")))
                .price(asNullableLong(product.path("price")))
                .stockLeft(stockLeft)
                .soldCount(soldCount)
                .status(resolveSalesStatus(stockLeft))
                .description(textOrNull(product.path("description")))
                .build();
    }

    private SalesProductListItemResponse buildListItem(Long itemId) {
        JsonNode product = fetchProduct(itemId);
        if (product == null) {
            return null;
        }

        int stockLeft = fetchStockLeft(itemId);
        long soldCount = purchaseRepository.countByItemIdAndStatusIn(itemId, SOLD_COUNT_STATUSES);

        return SalesProductListItemResponse.builder()
                .id(itemId)
                .title(textOrNull(product.path("title")))
                .fundingTitle(null)
                .storeName(null)
                .category(null)
                .thumbnailUrl(null)
                .thumbnailMediaId(asNullableLong(product.path("images").path("thumbnail").path("mediaId")))
                .price(asNullableLong(product.path("price")))
                .stockLeft(stockLeft)
                .soldCount(soldCount)
                .status(resolveSalesStatus(stockLeft))
                .build();
    }

    private JsonNode fetchProduct(Long itemId) {
        try {
            return productItemQueryClientFacade.findItem(itemId);
        } catch (ProductClientException e) {
            return null;
        }
    }

    private int fetchStockLeft(Long itemId) {
        try {
            return stockAvailabilityQueryFacade.fetchAvailableStockTotal(itemId);
        } catch (StockClientException e) {
            return 0;
        }
    }

    private String resolveSalesStatus(int stockLeft) {
        if (stockLeft <= 0) {
            return "SOLD_OUT";
        }
        if (stockLeft <= 10) {
            return "LOW_STOCK";
        }
        return "ON_SALE";
    }

    private int normalizeSize(Integer size) {
        int normalizedSize = size == null ? DEFAULT_SIZE : size;
        if (normalizedSize < 1 || normalizedSize > MAX_SIZE) {
            throw new BusinessException(SalesErrorCode.INVALID_REQUEST, "size는 1 이상 100 이하여야 합니다.");
        }
        return normalizedSize;
    }

    private Long decodeCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }
        try {
            return CursorUtils.decodeLong(cursor);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(SalesErrorCode.INVALID_REQUEST, "유효하지 않은 cursor 값입니다.");
        }
    }

    private Long asNullableLong(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        if (node.isTextual()) {
            String raw = node.asText(null);
            if (!StringUtils.hasText(raw)) {
                return null;
            }
            try {
                return Long.parseLong(raw.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.asText(null);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
