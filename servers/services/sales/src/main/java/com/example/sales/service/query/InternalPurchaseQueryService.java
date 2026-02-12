package com.example.sales.service.query;

import com.example.core.exception.BusinessException;
import com.example.sales.dto.response.PurchaseResponse;
import com.example.sales.entity.Purchase;
import com.example.sales.exception.SalesErrorCode;
import com.example.sales.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalPurchaseQueryService {

    private final PurchaseRepository purchaseRepository;

    public PurchaseResponse findById(Long purchaseId) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new BusinessException(SalesErrorCode.PURCHASE_NOT_FOUND));
        return PurchaseResponse.from(purchase);
    }

    public PurchaseResponse findByOrderId(Long orderId) {
        Purchase purchase = purchaseRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(SalesErrorCode.PURCHASE_NOT_FOUND));
        return PurchaseResponse.from(purchase);
    }
}
