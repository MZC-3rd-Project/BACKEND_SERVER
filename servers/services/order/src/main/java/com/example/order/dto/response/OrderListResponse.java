package com.example.order.dto.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.order.domain.Order;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class OrderListResponse {

    @SnowflakeId
    private Long orderId;

    @SnowflakeId
    private Long purchaseId;

    private Long totalAmount;
    private String status;
    private int itemCount;
    private LocalDateTime createdAt;

    public static OrderListResponse from(Order order) {
        return OrderListResponse.builder()
                .orderId(order.getId())
                .purchaseId(order.getPurchaseId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .itemCount(order.getOrderItems().size())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
