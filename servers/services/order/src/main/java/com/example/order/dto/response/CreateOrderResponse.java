package com.example.order.dto.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.order.domain.Order;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CreateOrderResponse {

    @SnowflakeId
    private Long orderId;

    @SnowflakeId
    private Long purchaseId;

    private String status;
    private Long totalAmount;
    private LocalDateTime createdAt;

    public static CreateOrderResponse from(Order order) {
        return CreateOrderResponse.builder()
                .orderId(order.getId())
                .purchaseId(order.getPurchaseId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
