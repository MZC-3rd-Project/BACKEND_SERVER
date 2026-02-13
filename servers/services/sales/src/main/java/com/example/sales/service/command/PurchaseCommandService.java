package com.example.sales.service.command;

import com.example.core.exception.BusinessException;
import com.example.core.id.Snowflake;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.sales.client.ProductClient;
import com.example.sales.client.StockClient;
import com.example.sales.dto.request.PurchaseRequest;
import com.example.sales.dto.response.PurchaseResponse;
import com.example.sales.entity.Purchase;
import com.example.sales.event.PurchaseCancelledEvent;
import com.example.sales.event.PurchaseCreatedEvent;
import com.example.sales.exception.SalesErrorCode;
import com.example.sales.repository.PurchaseRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseCommandService {

    private final PurchaseRepository purchaseRepository;
    private final ProductClient productClient;
    private final StockClient stockClient;
    private final EventPublisher eventPublisher;
    private final Snowflake snowflake;
    private final TransactionTemplate transactionTemplate;

    public PurchaseResponse purchase(PurchaseRequest request, Long userId) {
        JsonNode itemData = productClient.findItem(request.getItemId());
        String itemStatus = itemData.path("status").asText();
        if (!"ON_SALE".equals(itemStatus)) {
            throw new BusinessException(SalesErrorCode.PURCHASE_NOT_FOUND);
        }

        Long unitPrice = itemData.path("price").asLong();
        Long totalAmount = unitPrice * request.getQuantity();

        Long orderId = snowflake.nextId();

        Long reservationId = stockClient.reserveStock(
                request.getStockItemId(), userId, request.getQuantity(), orderId);

        return transactionTemplate.execute(status -> {
            Purchase purchase = Purchase.create(
                    userId, request.getItemId(), request.getStockItemId(),
                    request.getReferenceId(), request.getQuantity(),
                    unitPrice, totalAmount, orderId, reservationId
            );

            purchaseRepository.save(purchase);

            eventPublisher.publish(
                    new PurchaseCreatedEvent(
                            purchase.getId(), orderId, userId,
                            request.getItemId(), totalAmount, request.getQuantity()
                    ),
                    EventMetadata.of("Purchase", String.valueOf(purchase.getId()))
            );

            return PurchaseResponse.from(purchase);
        });
    }

    public void cancel(Long purchaseId, Long userId) {
        Purchase existingPurchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new BusinessException(SalesErrorCode.PURCHASE_NOT_FOUND));

        if (!existingPurchase.getUserId().equals(userId)) {
            throw new BusinessException(SalesErrorCode.PURCHASE_NOT_CANCELLABLE);
        }

        if (existingPurchase.getReservationId() != null) {
            stockClient.cancelReservation(existingPurchase.getReservationId());
        }

        transactionTemplate.executeWithoutResult(status -> cancelInTransaction(purchaseId, userId));
    }

    private void cancelInTransaction(Long purchaseId, Long userId) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new BusinessException(SalesErrorCode.PURCHASE_NOT_FOUND));

        if (!purchase.getUserId().equals(userId)) {
            throw new BusinessException(SalesErrorCode.PURCHASE_NOT_CANCELLABLE);
        }

        purchase.cancel();

        eventPublisher.publish(
                new PurchaseCancelledEvent(
                        purchase.getId(), purchase.getOrderId(),
                        userId, purchase.getReservationId()
                ),
                EventMetadata.of("Purchase", String.valueOf(purchase.getId()))
        );
    }
}
