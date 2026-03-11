package com.example.order.dto.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.order.domain.Order;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class InternalCreateOrderResponse {

    @SnowflakeId
    private Long orderId;

    private String status;

    private LocalDateTime createdAt;

    public static InternalCreateOrderResponse from(Order order) {
        return InternalCreateOrderResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus().name())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
