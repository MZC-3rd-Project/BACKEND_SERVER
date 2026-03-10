package com.example.sales.service.command;

import com.example.clients.product.dto.ProductQuoteLineItemRequest;
import com.example.clients.product.dto.ProductQuoteRequest;
import com.example.clients.product.dto.ProductQuoteResponse;
import com.example.clients.product.dto.ProductQuotedLineItem;
import com.example.clients.product.exception.ProductClientException;
import com.example.clients.product.facade.ProductQuoteClientFacade;
import com.example.clients.stock.dto.ReserveOrderStockLineItem;
import com.example.clients.stock.dto.ReserveOrderStockRequest;
import com.example.clients.stock.dto.ReserveOrderStockResponse;
import com.example.clients.stock.dto.ReservedOrderStockLineItem;
import com.example.clients.stock.exception.StockClientConflictException;
import com.example.clients.stock.exception.StockClientException;
import com.example.clients.stock.facade.StockOrderReservationClientFacade;
import com.example.core.exception.BusinessException;
import com.example.sales.dto.checkout.CheckoutDraft;
import com.example.sales.dto.checkout.request.CheckoutQuoteRequest;
import com.example.sales.dto.checkout.request.CheckoutReserveRequest;
import com.example.sales.dto.checkout.response.CheckoutQuoteResponse;
import com.example.sales.dto.checkout.response.CheckoutReserveResponse;
import com.example.sales.exception.SalesErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CheckoutCommandService {

    private final StockOrderReservationClientFacade stockOrderReservationClientFacade;
    private final ProductQuoteClientFacade productQuoteClientFacade;
    private final CheckoutDraftRedisService checkoutDraftRedisService;

    public CheckoutReserveResponse reserve(CheckoutReserveRequest request, Long userId) {
        CheckoutDraft idempotentDraft = findReusableDraft(userId, request.getIdempotencyKey());
        if (idempotentDraft != null) {
            return toReserveResponse(idempotentDraft);
        }

        ReserveOrderStockResponse stockResponse;
        try {
            stockResponse = stockOrderReservationClientFacade.reserveOrderStock(new ReserveOrderStockRequest(
                    request.getChannelType(),
                    request.getChannelRefId(),
                    userId,
                    request.getIdempotencyKey(),
                    request.getLineItems().stream()
                            .map(item -> new ReserveOrderStockLineItem(
                                    item.getItemId(),
                                    item.getStockItemType(),
                                    item.getReferenceId(),
                                    item.getQuantity()))
                            .toList()
            ));
        } catch (StockClientConflictException e) {
            throw new BusinessException(SalesErrorCode.STOCK_INSUFFICIENT);
        } catch (StockClientException e) {
            throw new BusinessException(SalesErrorCode.STOCK_SERVICE_ERROR);
        }

        CheckoutDraft draft = CheckoutDraft.builder()
                .orderId(stockResponse.orderId())
                .userId(userId)
                .channelType(request.getChannelType())
                .channelRefId(request.getChannelRefId())
                .idempotencyKey(request.getIdempotencyKey())
                .expiresAt(stockResponse.expiresAt())
                .lineItems(request.getLineItems().stream()
                        .map(item -> CheckoutDraft.LineItem.builder()
                                .itemId(item.getItemId())
                                .stockItemType(item.getStockItemType())
                                .referenceId(item.getReferenceId())
                                .quantity(item.getQuantity())
                                .build())
                        .toList())
                .build();

        checkoutDraftRedisService.saveDraft(draft, ttlUntil(stockResponse.expiresAt()));
        return CheckoutReserveResponse.builder()
                .orderId(stockResponse.orderId())
                .expiresAt(stockResponse.expiresAt())
                .reservedItems(stockResponse.reservedItems().stream()
                        .map(this::toReservedLineItem)
                        .toList())
                .build();
    }

    public CheckoutQuoteResponse quote(CheckoutQuoteRequest request, Long userId) {
        CheckoutDraft draft = checkoutDraftRedisService.findDraft(request.getOrderId())
                .orElseThrow(() -> new BusinessException(SalesErrorCode.CHECKOUT_DRAFT_NOT_FOUND));

        if (!draft.getUserId().equals(userId)) {
            throw new BusinessException(SalesErrorCode.CHECKOUT_DRAFT_FORBIDDEN);
        }

        if (draft.getExpiresAt() != null && LocalDateTime.now().isAfter(draft.getExpiresAt())) {
            checkoutDraftRedisService.deleteDraft(draft.getOrderId());
            throw new BusinessException(SalesErrorCode.CHECKOUT_DRAFT_EXPIRED);
        }

        ProductQuoteResponse quoteResponse;
        try {
            quoteResponse = productQuoteClientFacade.quoteItems(new ProductQuoteRequest(
                    draft.getChannelType(),
                    draft.getChannelRefId(),
                    draft.getLineItems().stream()
                            .map(item -> new ProductQuoteLineItemRequest(item.getItemId(), item.getReferenceId(), item.getQuantity()))
                            .toList()
            ));
        } catch (ProductClientException e) {
            throw new BusinessException(SalesErrorCode.PRODUCT_SERVICE_ERROR);
        }

        return CheckoutQuoteResponse.builder()
                .orderId(draft.getOrderId())
                .expiresAt(draft.getExpiresAt())
                .quotedAt(quoteResponse.quotedAt())
                .totalAmount(quoteResponse.totalAmount())
                .lineItems(quoteResponse.lineItems().stream()
                        .map(this::toQuotedLineItem)
                        .toList())
                .build();
    }

    private CheckoutDraft findReusableDraft(Long userId, String idempotencyKey) {
        return checkoutDraftRedisService.findOrderIdByIdempotencyKey(userId, idempotencyKey)
                .flatMap(checkoutDraftRedisService::findDraft)
                .filter(draft -> draft.getExpiresAt() == null || LocalDateTime.now().isBefore(draft.getExpiresAt()))
                .orElse(null);
    }

    private Duration ttlUntil(LocalDateTime expiresAt) {
        Duration ttl = Duration.between(LocalDateTime.now(), expiresAt);
        return ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(1) : ttl;
    }

    private CheckoutReserveResponse toReserveResponse(CheckoutDraft draft) {
        return CheckoutReserveResponse.builder()
                .orderId(draft.getOrderId())
                .expiresAt(draft.getExpiresAt())
                .reservedItems(draft.getLineItems().stream()
                        .map(item -> CheckoutReserveResponse.ReservedLineItem.builder()
                                .itemId(item.getItemId())
                                .stockItemType(item.getStockItemType())
                                .referenceId(item.getReferenceId())
                                .quantity(item.getQuantity())
                                .build())
                        .toList())
                .build();
    }

    private CheckoutReserveResponse.ReservedLineItem toReservedLineItem(ReservedOrderStockLineItem item) {
        return CheckoutReserveResponse.ReservedLineItem.builder()
                .itemId(item.itemId())
                .stockItemType(item.stockItemType())
                .referenceId(item.referenceId())
                .quantity(item.quantity())
                .build();
    }

    private CheckoutQuoteResponse.QuotedLineItem toQuotedLineItem(ProductQuotedLineItem item) {
        return CheckoutQuoteResponse.QuotedLineItem.builder()
                .itemId(item.itemId())
                .itemType(item.itemType())
                .title(item.title())
                .sellerId(item.sellerId())
                .storeId(item.storeId())
                .referenceId(item.referenceId())
                .referenceName(item.referenceName())
                .stockItemType(item.stockItemType())
                .quantity(item.quantity())
                .baseUnitPrice(item.baseUnitPrice())
                .finalUnitPrice(item.finalUnitPrice())
                .lineAmount(item.lineAmount())
                .build();
    }
}
