package com.example.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class InternalCreateOrderRequest {

    @NotNull
    private Long orderId;

    @NotNull
    private Long userId;

    private LocalDateTime expiresAt;

    @NotNull
    private Long totalAmount;

    private String recipientName;
    private String recipientPhone;
    private Long deliveryAddressId;
    private String deliveryMemo;

    @NotEmpty
    @Valid
    private List<LineItem> lineItems;

    @Getter
    @NoArgsConstructor
    public static class LineItem {
        private String channelType;
        private Long channelRefId;
        @NotNull
        private Long itemId;
        private String itemType;
        private String title;
        private Long sellerId;
        @NotNull
        private Long storeId;
        private String stockItemType;
        private Long referenceId;
        private String referenceName;
        @NotNull
        private Integer quantity;
        private Long baseUnitPrice;
        @NotNull
        private Long finalUnitPrice;
        @NotNull
        private Long lineAmount;
    }
}
