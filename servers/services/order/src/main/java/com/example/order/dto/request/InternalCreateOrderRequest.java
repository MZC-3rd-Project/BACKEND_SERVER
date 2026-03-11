package com.example.order.dto.request;

import com.example.order.domain.ChannelType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class InternalCreateOrderRequest {

    @NotNull(message = "주문 ID(orderId)는 필수입니다")
    private Long orderId;

    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;

    private LocalDateTime expiresAt;

    @NotNull(message = "총 금액은 필수입니다")
    @Min(value = 0, message = "총 금액은 0 이상이어야 합니다")
    private Long totalAmount;

    private String recipientName;
    private String recipientPhone;
    private Long deliveryAddressId;
    private String deliveryMemo;

    @NotEmpty(message = "주문 항목은 최소 1개 이상이어야 합니다")
    @Valid
    private List<LineItem> lineItems;

    @Getter
    @NoArgsConstructor
    public static class LineItem {

        @NotNull(message = "채널 타입은 필수입니다")
        private ChannelType channelType;

        private Long channelRefId;

        @NotNull(message = "상품 ID는 필수입니다")
        private Long itemId;

        private String itemType;
        private String title;
        private Long sellerId;

        @NotNull(message = "매장 ID는 필수입니다")
        private Long storeId;

        private String stockItemType;
        private Long referenceId;
        private String referenceName;

        @NotNull(message = "수량은 필수입니다")
        @Min(value = 1, message = "수량은 1 이상이어야 합니다")
        private Integer quantity;

        private Long baseUnitPrice;

        @NotNull(message = "최종 단가는 필수입니다")
        @Min(value = 0, message = "최종 단가는 0 이상이어야 합니다")
        private Long finalUnitPrice;

        @NotNull(message = "라인 금액은 필수입니다")
        @Min(value = 0, message = "라인 금액은 0 이상이어야 합니다")
        private Long lineAmount;
    }
}
