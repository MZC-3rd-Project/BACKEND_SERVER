package com.example.sales.service.query;

import com.example.core.exception.BusinessException;
import com.example.core.pagination.CursorResponse;
import com.example.core.pagination.CursorUtils;
import com.example.sales.dto.response.PurchaseResponse;
import com.example.sales.entity.Purchase;
import com.example.sales.exception.SalesErrorCode;
import com.example.sales.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseQueryService {

    private final PurchaseRepository purchaseRepository;

    public CursorResponse<PurchaseResponse> findByUserId(Long userId, String cursor, int size) {
        Long cursorId = CursorUtils.decodeLong(cursor);
        PageRequest pageable = PageRequest.of(0, size + 1);

        List<Purchase> purchases = purchaseRepository.findByUserIdWithCursor(userId, cursorId, pageable);

        boolean hasNext = purchases.size() > size;
        List<Purchase> pageItems = hasNext ? purchases.subList(0, size) : purchases;

        List<PurchaseResponse> content = pageItems.stream()
                .map(PurchaseResponse::from)
                .toList();

        String nextCursor = hasNext
                ? CursorUtils.encode(pageItems.get(pageItems.size() - 1).getId())
                : null;

        return CursorResponse.of(content, nextCursor);
    }

    public PurchaseResponse findById(Long purchaseId, Long userId) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new BusinessException(SalesErrorCode.PURCHASE_NOT_FOUND));

        if (!purchase.getUserId().equals(userId)) {
            throw new BusinessException(SalesErrorCode.PURCHASE_NOT_FOUND);
        }

        return PurchaseResponse.from(purchase);
    }
}
