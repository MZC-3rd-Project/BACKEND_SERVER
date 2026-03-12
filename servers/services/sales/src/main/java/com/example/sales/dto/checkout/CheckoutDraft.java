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
public class CheckoutDraft {

    private Long orderId;
    private Long userId;
    private String idempotencyKey;
    private LocalDateTime expiresAt;
    private List<LineItem> lineItems;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItem {
        private Long itemId;
        private String channelType;
        private Long channelRefId;
        private String stockItemType;
        private Long referenceId;
        private Integer quantity;
    }
}
