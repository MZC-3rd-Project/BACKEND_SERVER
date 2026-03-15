package com.example.product.service.query.quote;

import com.example.core.exception.BusinessException;
import com.example.product.entity.item.ItemStatus;
import com.example.product.exception.ProductErrorCode;

import java.util.Locale;

public enum QuoteChannelType {
    NORMAL(ItemStatus.ON_SALE),
    FUNDING(ItemStatus.FUNDING);

    private final ItemStatus saleableStatus;

    QuoteChannelType(ItemStatus saleableStatus) {
        this.saleableStatus = saleableStatus;
    }

    public ItemStatus saleableStatus() {
        return saleableStatus;
    }

    public static QuoteChannelType from(String rawChannelType) {
        if (rawChannelType == null || rawChannelType.isBlank()) {
            throw new BusinessException(ProductErrorCode.INVALID_QUOTE_CHANNEL);
        }

        try {
            return QuoteChannelType.valueOf(rawChannelType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ProductErrorCode.INVALID_QUOTE_CHANNEL);
        }
    }
}
