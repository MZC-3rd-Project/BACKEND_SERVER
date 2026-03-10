package com.example.sales.service.command;

import com.example.core.exception.BusinessException;
import com.example.core.id.Snowflake;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.clients.product.exception.ProductClientException;
import com.example.clients.product.facade.ProductItemQueryClientFacade;
import com.example.clients.stock.exception.StockClientConflictException;
import com.example.clients.stock.exception.StockClientException;
import com.example.clients.stock.facade.StockReservationClientFacade;
import com.example.sales.dto.request.PurchaseRequest;
import com.example.sales.dto.response.PurchaseResponse;
import com.example.sales.entity.Purchase;
import com.example.sales.event.PurchaseCancelledEvent;
import com.example.sales.event.PurchaseCreatedEvent;
import com.example.sales.exception.SalesErrorCode;
import com.example.sales.repository.PurchaseRepository;
import com.example.sales.service.retry.StockCancelRetryService;
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
    private final ProductItemQueryClientFacade productClient;
    private final StockReservationClientFacade stockClient;
    private final EventPublisher eventPublisher;
    private final Snowflake snowflake;
    private final TransactionTemplate transactionTemplate;
    private final StockCancelRetryService stockCancelRetryService;

    public PurchaseResponse purchase(PurchaseRequest request, Long userId) {
        JsonNode itemData;
        try {
            itemData = productClient.findItem(request.getItemId());
        } catch (ProductClientException e) {
            throw new BusinessException(SalesErrorCode.PRODUCT_SERVICE_ERROR);
        }

        String itemStatus = itemData.path("status").asText();
        if (!"ON_SALE".equals(itemStatus)) {
            throw new BusinessException(SalesErrorCode.PURCHASE_NOT_FOUND);
        }

        Long unitPrice = itemData.path("price").asLong();
        Long totalAmount = unitPrice * request.getQuantity();

        Long orderId = snowflake.nextId();
        try {
            stockClient.reserveStock(request.getStockItemId(), userId, request.getQuantity(), orderId);
        } catch (StockClientConflictException e) {
            throw new BusinessException(SalesErrorCode.STOCK_INSUFFICIENT);
        } catch (StockClientException e) {
            throw new BusinessException(SalesErrorCode.STOCK_SERVICE_ERROR);
        }

        try {
            return transactionTemplate.execute(status -> {
                Purchase purchase = Purchase.create(
                        userId, request.getItemId(), request.getStockItemId(),
                        request.getReferenceId(), request.getQuantity(),
                        unitPrice, totalAmount, orderId
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
        } catch (RuntimeException txException) {
            compensateReservedStockOnCreateFailure(orderId, txException);
            throw txException;
        }
    }

    public void cancel(Long purchaseId, Long userId) {
        Long orderId = transactionTemplate.execute(status -> cancelInTransaction(purchaseId, userId));
        cancelReservationAfterCommit(purchaseId, orderId);
    }

    private Long cancelInTransaction(Long purchaseId, Long userId) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new BusinessException(SalesErrorCode.PURCHASE_NOT_FOUND));

        if (!purchase.getUserId().equals(userId)) {
            throw new BusinessException(SalesErrorCode.PURCHASE_NOT_CANCELLABLE);
        }

        purchase.cancel();

        eventPublisher.publish(
                new PurchaseCancelledEvent(
                        purchase.getId(), purchase.getOrderId(),
                        userId
                ),
                EventMetadata.of("Purchase", String.valueOf(purchase.getId()))
        );

        return purchase.getOrderId();
    }

    private void cancelReservationAfterCommit(Long purchaseId, Long orderId) {
        if (orderId == null) {
            return;
        }

        try {
            stockClient.cancelReservationsByOrderId(orderId);
        } catch (Exception e) {
            // Local cancellation has been committed. Keep it and alert for stock reconciliation.
            log.error("Purchase cancelled but stock reservation cancel failed: purchaseId={}, orderId={}",
                    purchaseId, orderId, e);
            stockCancelRetryService.enqueue(purchaseId, orderId, e.getMessage());
        }
    }

    private void compensateReservedStockOnCreateFailure(Long orderId, Throwable txException) {
        try {
            stockClient.cancelReservationsByOrderId(orderId);
            log.warn("Purchase create failed after stock reserve; order reservations cancelled: orderId={}",
                    orderId, txException);
        } catch (Exception cancelException) {
            Long retryPurchaseId = orderId;
            String fallbackSource = "orderId";
            if (retryPurchaseId == null) {
                retryPurchaseId = snowflake.nextId();
                fallbackSource = "generatedSnowflake";
            }

            String retryReason = "Purchase create tx failed (" + safeErrorMessage(txException)
                    + "), immediate cancel failed (" + safeErrorMessage(cancelException)
                    + "), fallbackPurchaseIdSource=" + fallbackSource;

            log.error("Purchase create compensation failed; enqueue stock cancel retry: orderId={}, retryPurchaseId={}, fallbackSource={}",
                    orderId, retryPurchaseId, fallbackSource, cancelException);
            try {
                stockCancelRetryService.enqueue(retryPurchaseId, orderId, retryReason);
            } catch (Exception enqueueException) {
                log.error("Failed to enqueue stock cancel retry after purchase create failure: orderId={}, retryPurchaseId={}",
                        orderId, retryPurchaseId, enqueueException);
            }
        }
    }

    private String safeErrorMessage(Throwable exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getMessage();
    }
}
