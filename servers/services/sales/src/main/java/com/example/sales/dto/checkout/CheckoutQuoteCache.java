package com.example.sales.dto.checkout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutQuoteCache {

    private Long orderId;
    private LocalDateTime expiresAt;
    private LocalDateTime quotedAt;
    private Long totalAmount;
    private List<LineItem> lineItems;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItem {
        private Long itemId;
        private String itemType;
        private String title;
        private Long sellerId;
        private Long storeId;
        private Long referenceId;
        private String referenceName;
        private String stockItemType;
        private Integer quantity;
        private Long baseUnitPrice;
        private Long finalUnitPrice;
        private Long lineAmount;
    }
}
