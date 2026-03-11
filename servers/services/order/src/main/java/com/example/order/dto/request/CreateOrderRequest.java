package com.example.order.dto.request;

import com.example.order.domain.ChannelType;
import com.example.order.domain.ItemType;
import com.example.order.domain.OrderType;
import com.example.order.domain.StockItemType;
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
public class CreateOrderRequest {

    @NotNull(message = "주문 ID(orderId)는 필수입니다")
    private Long orderId;

    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;

    @NotNull(message = "주문 타입은 필수입니다")
    private OrderType orderType;

    @NotNull(message = "총 금액은 필수입니다")
    @Min(value = 0, message = "총 금액은 0 이상이어야 합니다")
    private Long totalAmount;

    private LocalDateTime expiresAt;

    @NotEmpty(message = "주문 항목은 최소 1개 이상이어야 합니다")
    @Valid
    private List<OrderItemRequest> items;

    // --- 배송지 / 수령인 (선택) ---
    private String recipientName;
    private String recipientPhone;
    private String zipCode;
    private String address;
    private String addressDetail;
    private String deliveryMemo;

    @Getter
    @NoArgsConstructor
    public static class OrderItemRequest {

        @NotNull(message = "채널 타입은 필수입니다")
        private ChannelType channelType;

        private Long channelRefId;

        @NotNull(message = "상품 ID는 필수입니다")
        private Long itemId;

        @NotNull(message = "상품 타입은 필수입니다")
        private ItemType itemType;

        @NotNull(message = "상품명은 필수입니다")
        private String title;

        private Long sellerId;

        private Long storeId;

        private StockItemType stockItemType;

        private Long referenceId;

        private String referenceName;

        @NotNull(message = "수량은 필수입니다")
        @Min(value = 1, message = "수량은 1 이상이어야 합니다")
        private Integer quantity;

        @NotNull(message = "기본 단가는 필수입니다")
        @Min(value = 0, message = "기본 단가는 0 이상이어야 합니다")
        private Long baseUnitPrice;

        @NotNull(message = "최종 단가는 필수입니다")
        @Min(value = 0, message = "최종 단가는 0 이상이어야 합니다")
        private Long finalUnitPrice;

        @NotNull(message = "라인 금액은 필수입니다")
        @Min(value = 0, message = "라인 금액은 0 이상이어야 합니다")
        private Long lineAmount;
    }
}
