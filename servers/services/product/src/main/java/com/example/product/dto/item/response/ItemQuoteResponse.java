package com.example.product.dto.item.response;

import com.example.core.id.jackson.SnowflakeId;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ItemQuoteResponse {

    private LocalDateTime quotedAt;
    private Long totalAmount;
    private List<QuotedLineItem> lineItems;

    @Getter
    @Builder
    public static class QuotedLineItem {

        @SnowflakeId
        private Long itemId;

        private String itemType;
        private String title;

        @SnowflakeId
        private Long sellerId;

        @SnowflakeId
        private Long storeId;

        @SnowflakeId
        private Long referenceId;

        private String referenceName;
        private String stockItemType;
        private Integer quantity;
        private Long baseUnitPrice;
        private Long finalUnitPrice;
        private Long lineAmount;
    }
}
