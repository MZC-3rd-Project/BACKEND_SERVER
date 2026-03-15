package com.example.sales.service.support;

import com.example.clients.product.dto.ProductQuoteResponse;
import com.example.clients.product.dto.ProductQuotedLineItem;
import com.example.sales.domain.checkout.CheckoutLineItemKey;
import com.example.sales.domain.checkout.QuoteSnapshot;
import com.example.sales.dto.checkout.CheckoutDraft;
import com.example.sales.dto.checkout.CheckoutQuoteCache;
import com.example.sales.dto.checkout.response.CheckoutQuoteResponse;
import com.example.sales.entity.CheckoutSession;
import com.example.sales.entity.CheckoutSessionLineItem;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class CheckoutSnapshotAssembler {

    public CheckoutDraft toDraft(CheckoutSession session) {
        return CheckoutDraft.builder()
                .orderId(session.getOrderId())
                .userId(session.getUserId())
                .idempotencyKey(session.getIdempotencyKey())
                .expiresAt(session.getExpiresAt())
                .lineItems(session.getLineItems().stream()
                        .map(item -> CheckoutDraft.LineItem.builder()
                                .itemId(item.getItemId())
                                .channelType(item.getChannelType())
                                .channelRefId(item.getChannelRefId())
                                .stockItemType(item.getStockItemType())
                                .referenceId(item.getReferenceId())
                                .quantity(item.getQuantity())
                                .build())
                        .toList())
                .build();
    }

    public QuoteSnapshot toQuoteSnapshot(CheckoutDraft draft, ProductQuoteResponse quoteResponse) {
        return new QuoteSnapshot(
                draft.getOrderId(),
                draft.getExpiresAt(),
                quoteResponse.quotedAt(),
                quoteResponse.totalAmount(),
                quoteResponse.lineItems().stream()
                        .map(this::toQuotedLineItem)
                        .toList()
        );
    }

    public QuoteSnapshot toQuoteSnapshot(CheckoutSession session, LocalDateTime expiresAt) {
        long totalAmount = session.getLineItems().stream()
                .map(CheckoutSessionLineItem::getLineAmount)
                .filter(amount -> amount != null)
                .mapToLong(Long::longValue)
                .sum();

        return new QuoteSnapshot(
                session.getOrderId(),
                expiresAt,
                session.getQuotedAt(),
                totalAmount,
                session.getLineItems().stream()
                        .map(this::toQuotedLineItem)
                        .toList()
        );
    }

    public QuoteSnapshot toQuoteSnapshot(CheckoutQuoteCache quoteCache) {
        return new QuoteSnapshot(
                quoteCache.getOrderId(),
                quoteCache.getExpiresAt(),
                quoteCache.getQuotedAt(),
                quoteCache.getTotalAmount(),
                quoteCache.getLineItems().stream()
                        .map(this::toQuotedLineItem)
                        .toList()
        );
    }

    public CheckoutQuoteCache toQuoteCache(QuoteSnapshot snapshot) {
        return CheckoutQuoteCache.builder()
                .orderId(snapshot.orderId())
                .expiresAt(snapshot.expiresAt())
                .quotedAt(snapshot.quotedAt())
                .totalAmount(snapshot.totalAmount())
                .lineItems(snapshot.lineItems().stream()
                        .map(item -> CheckoutQuoteCache.LineItem.builder()
                                .itemId(item.key().itemId())
                                .itemType(item.itemType())
                                .title(item.title())
                                .sellerId(item.sellerId())
                                .storeId(item.storeId())
                                .referenceId(item.key().referenceId())
                                .referenceName(item.referenceName())
                                .stockItemType(item.stockItemType())
                                .quantity(item.quantity())
                                .baseUnitPrice(item.baseUnitPrice())
                                .finalUnitPrice(item.finalUnitPrice())
                                .lineAmount(item.lineAmount())
                                .build())
                        .toList())
                .build();
    }

    public CheckoutQuoteResponse toResponse(QuoteSnapshot snapshot) {
        return CheckoutQuoteResponse.builder()
                .orderId(snapshot.orderId())
                .expiresAt(snapshot.expiresAt())
                .quotedAt(snapshot.quotedAt())
                .totalAmount(snapshot.totalAmount())
                .lineItems(snapshot.lineItems().stream()
                        .map(item -> CheckoutQuoteResponse.QuotedLineItem.builder()
                                .itemId(item.key().itemId())
                                .itemType(item.itemType())
                                .title(item.title())
                                .sellerId(item.sellerId())
                                .storeId(item.storeId())
                                .referenceId(item.key().referenceId())
                                .referenceName(item.referenceName())
                                .stockItemType(item.stockItemType())
                                .quantity(item.quantity())
                                .baseUnitPrice(item.baseUnitPrice())
                                .finalUnitPrice(item.finalUnitPrice())
                                .lineAmount(item.lineAmount())
                                .build())
                        .toList())
                .build();
    }

    private QuoteSnapshot.QuotedLineItem toQuotedLineItem(ProductQuotedLineItem item) {
        return new QuoteSnapshot.QuotedLineItem(
                new CheckoutLineItemKey(item.itemId(), item.referenceId()),
                item.itemType(),
                item.title(),
                item.sellerId(),
                item.storeId(),
                item.referenceName(),
                item.stockItemType(),
                item.quantity(),
                item.baseUnitPrice(),
                item.finalUnitPrice(),
                item.lineAmount()
        );
    }

    private QuoteSnapshot.QuotedLineItem toQuotedLineItem(CheckoutSessionLineItem item) {
        return new QuoteSnapshot.QuotedLineItem(
                item.key(),
                item.getItemType(),
                item.getTitle(),
                item.getSellerId(),
                item.getStoreId(),
                item.getReferenceName(),
                item.getStockItemType(),
                item.getQuantity(),
                item.getBaseUnitPrice(),
                item.getFinalUnitPrice(),
                item.getLineAmount()
        );
    }

    private QuoteSnapshot.QuotedLineItem toQuotedLineItem(CheckoutQuoteCache.LineItem item) {
        return new QuoteSnapshot.QuotedLineItem(
                new CheckoutLineItemKey(item.getItemId(), item.getReferenceId()),
                item.getItemType(),
                item.getTitle(),
                item.getSellerId(),
                item.getStoreId(),
                item.getReferenceName(),
                item.getStockItemType(),
                item.getQuantity(),
                item.getBaseUnitPrice(),
                item.getFinalUnitPrice(),
                item.getLineAmount()
        );
    }
}
