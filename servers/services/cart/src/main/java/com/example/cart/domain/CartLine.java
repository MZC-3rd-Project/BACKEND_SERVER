package com.example.cart.domain;

import java.time.Instant;
import lombok.Getter;

@Getter
public class CartLine {

    private final CartLineIdentity identity;
    private String stockItemType;
    private int quantity;
    private boolean selected;
    private Long storeId;
    private String itemTitle;
    private String thumbnailUrl;
    private String storeName;
    private Long displayPrice;
    private String salesStatus;
    private Instant createdAt;
    private Instant updatedAt;
    private Long expiresAtEpoch;
    private Long version;

    private CartLine(
            CartLineIdentity identity,
            String stockItemType,
            int quantity,
            boolean selected,
            Long storeId,
            String itemTitle,
            String thumbnailUrl,
            String storeName,
            Long displayPrice,
            String salesStatus,
            Instant createdAt,
            Instant updatedAt,
            Long expiresAtEpoch,
            Long version
    ) {
        this.identity = identity;
        this.stockItemType = normalizeStockItemType(stockItemType);
        this.quantity = validateQuantity(quantity);
        this.selected = selected;
        this.storeId = storeId;
        this.itemTitle = itemTitle;
        this.thumbnailUrl = thumbnailUrl;
        this.storeName = storeName;
        this.displayPrice = displayPrice;
        this.salesStatus = salesStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.expiresAtEpoch = expiresAtEpoch;
        this.version = version;
    }

    public static CartLine create(
            CartLineIdentity identity,
            String stockItemType,
            int quantity,
            boolean selected,
            Long storeId,
            String itemTitle,
            String thumbnailUrl,
            String storeName,
            Long displayPrice,
            String salesStatus,
            Instant createdAt,
            Instant updatedAt,
            Long expiresAtEpoch
    ) {
        return new CartLine(
                identity,
                stockItemType,
                quantity,
                selected,
                storeId,
                itemTitle,
                thumbnailUrl,
                storeName,
                displayPrice,
                salesStatus,
                createdAt,
                updatedAt,
                expiresAtEpoch,
                null
        );
    }

    public static CartLine rehydrate(
            CartLineIdentity identity,
            String stockItemType,
            int quantity,
            boolean selected,
            Long storeId,
            String itemTitle,
            String thumbnailUrl,
            String storeName,
            Long displayPrice,
            String salesStatus,
            Instant createdAt,
            Instant updatedAt,
            Long expiresAtEpoch,
            Long version
    ) {
        return new CartLine(
                identity,
                stockItemType,
                quantity,
                selected,
                storeId,
                itemTitle,
                thumbnailUrl,
                storeName,
                displayPrice,
                salesStatus,
                createdAt,
                updatedAt,
                expiresAtEpoch,
                version
        );
    }

    public CartLine merge(CartLine incoming, Instant now, long newExpiresAtEpoch) {
        this.stockItemType = normalizeStockItemType(incoming.stockItemType);
        this.quantity = validateQuantity(this.quantity + incoming.quantity);
        this.selected = incoming.selected;
        refreshSnapshot(
                incoming.storeId,
                incoming.itemTitle,
                incoming.thumbnailUrl,
                incoming.storeName,
                incoming.displayPrice,
                incoming.salesStatus
        );
        touch(now, newExpiresAtEpoch);
        return this;
    }

    public CartLine changeQuantity(int quantity, Instant now, long newExpiresAtEpoch) {
        this.quantity = validateQuantity(quantity);
        touch(now, newExpiresAtEpoch);
        return this;
    }

    public CartLine changeSelection(boolean selected, Instant now, long newExpiresAtEpoch) {
        this.selected = selected;
        touch(now, newExpiresAtEpoch);
        return this;
    }

    public CartLine refreshSnapshot(
            Long storeId,
            String itemTitle,
            String thumbnailUrl,
            String storeName,
            Long displayPrice,
            String salesStatus
    ) {
        this.storeId = storeId;
        this.itemTitle = itemTitle;
        this.thumbnailUrl = thumbnailUrl;
        this.storeName = storeName;
        this.displayPrice = displayPrice;
        this.salesStatus = salesStatus;
        return this;
    }

    public boolean isExpired(long epochSeconds) {
        return expiresAtEpoch != null && expiresAtEpoch <= epochSeconds;
    }

    public void touch(Instant now, long newExpiresAtEpoch) {
        this.updatedAt = now;
        this.expiresAtEpoch = newExpiresAtEpoch;
    }

    public void assignVersion(Long version) {
        this.version = version;
    }

    private int validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity는 1 이상이어야 합니다");
        }
        return quantity;
    }

    private String normalizeStockItemType(String stockItemType) {
        if (!org.springframework.util.StringUtils.hasText(stockItemType)) {
            throw new IllegalArgumentException("stockItemType은 비어 있을 수 없습니다");
        }
        return stockItemType.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
