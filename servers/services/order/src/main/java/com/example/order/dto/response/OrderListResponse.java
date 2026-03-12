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

    private String status;

    private Long totalAmount;

    private LocalDateTime createdAt;

    private int itemCount;

    public static OrderListResponse from(Order order) {
        return OrderListResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .itemCount(order.getOrderItems().size())
                .build();
    }
}
