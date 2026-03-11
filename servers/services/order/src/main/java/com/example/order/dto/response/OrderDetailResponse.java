package com.example.order.dto.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class OrderDetailResponse {

    @SnowflakeId
    private Long orderId;

    @SnowflakeId
    private Long userId;

    @SnowflakeId
    private Long purchaseId;

    private String orderType;

    @SnowflakeId
    private Long reservationId;

    @SnowflakeId
    private Long paymentId;

    private Long totalAmount;
    private String status;
    private LocalDateTime expiresAt;

    private String recipientName;
    private String recipientPhone;
    private String zipCode;
    private String address;
    private String addressDetail;
    private String deliveryMemo;

    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Builder
    public static class OrderItemResponse {

        @SnowflakeId
        private Long id;

        private String channelType;
        private Long channelRefId;

        @SnowflakeId
        private Long itemId;

        private String itemType;
        private String itemName;
        private Long sellerId;
        private Long storeId;
        private String stockItemType;
        private Long referenceId;
        private String referenceName;
        private Integer quantity;
        private Long baseUnitPrice;
        private Long unitPrice;
        private Long subtotal;

        public static OrderItemResponse from(OrderItem item) {
            return OrderItemResponse.builder()
                    .id(item.getId())
                    .channelType(item.getChannelType().name())
                    .channelRefId(item.getChannelRefId())
                    .itemId(item.getItemId())
                    .itemType(item.getItemType().name())
                    .itemName(item.getItemName())
                    .sellerId(item.getSellerId())
                    .storeId(item.getStoreId())
                    .stockItemType(item.getStockItemType() != null ? item.getStockItemType().name() : null)
                    .referenceId(item.getReferenceId())
                    .referenceName(item.getReferenceName())
                    .quantity(item.getQuantity())
                    .baseUnitPrice(item.getBaseUnitPrice())
                    .unitPrice(item.getUnitPrice())
                    .subtotal(item.getSubtotal())
                    .build();
        }
    }

    public static OrderDetailResponse from(Order order) {
        return OrderDetailResponse.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .purchaseId(order.getPurchaseId())
                .orderType(order.getOrderType().name())
                .reservationId(order.getReservationId())
                .paymentId(order.getPaymentId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .expiresAt(order.getExpiresAt())
                .recipientName(order.getRecipientName())
                .recipientPhone(order.getRecipientPhone())
                .zipCode(order.getZipCode())
                .address(order.getAddress())
                .addressDetail(order.getAddressDetail())
                .deliveryMemo(order.getDeliveryMemo())
                .items(order.getOrderItems().stream()
                        .map(OrderItemResponse::from)
                        .toList())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
