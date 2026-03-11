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
public class InternalOrderDetailResponse {

    @SnowflakeId
    private Long orderId;

    @SnowflakeId
    private Long userId;

    private String status;
    private Long totalAmount;

    private String recipientName;
    private String recipientPhone;
    @SnowflakeId
    private Long deliveryAddressId;
    private String deliveryMemo;

    private LocalDateTime expiresAt;

    private List<OrderDetailResponse.OrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static InternalOrderDetailResponse from(Order order) {
        return InternalOrderDetailResponse.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .recipientName(order.getRecipientName())
                .recipientPhone(order.getRecipientPhone())
                .deliveryAddressId(order.getDeliveryAddressId())
                .deliveryMemo(order.getDeliveryMemo())
                .expiresAt(order.getExpiresAt())
                .items(order.getOrderItems().stream()
                        .map(OrderDetailResponse.OrderItemResponse::from)
                        .toList())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
