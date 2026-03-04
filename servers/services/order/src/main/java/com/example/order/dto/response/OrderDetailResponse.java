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

    @SnowflakeId
    private Long reservationId;

    @SnowflakeId
    private Long paymentId;

    private Long totalAmount;
    private String status;
    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Builder
    public static class OrderItemResponse {

        @SnowflakeId
        private Long id;

        @SnowflakeId
        private Long itemId;

        private String itemName;
        private Integer quantity;
        private Long unitPrice;
        private Long subtotal;

        public static OrderItemResponse from(OrderItem item) {
            return OrderItemResponse.builder()
                    .id(item.getId())
                    .itemId(item.getItemId())
                    .itemName(item.getItemName())
                    .quantity(item.getQuantity())
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
                .reservationId(order.getReservationId())
                .paymentId(order.getPaymentId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .items(order.getOrderItems().stream()
                        .map(OrderItemResponse::from)
                        .toList())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
