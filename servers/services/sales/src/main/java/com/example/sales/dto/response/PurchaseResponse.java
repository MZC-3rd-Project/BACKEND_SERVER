package com.example.sales.dto.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.sales.entity.Purchase;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PurchaseResponse {

    @SnowflakeId
    private Long id;

    @SnowflakeId
    private Long userId;

    @SnowflakeId
    private Long itemId;

    @SnowflakeId
    private Long stockItemId;

    @SnowflakeId
    private Long referenceId;

    private Integer quantity;
    private Long unitPrice;
    private Long totalAmount;

    private String status;

    @SnowflakeId
    private Long orderId;

    @SnowflakeId
    private Long reservationId;

    @SnowflakeId
    private Long paymentId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PurchaseResponse from(Purchase p) {
        return PurchaseResponse.builder()
                .id(p.getId())
                .userId(p.getUserId())
                .itemId(p.getItemId())
                .stockItemId(p.getStockItemId())
                .referenceId(p.getReferenceId())
                .quantity(p.getQuantity())
                .unitPrice(p.getUnitPrice())
                .totalAmount(p.getTotalAmount())
                .status(p.getStatus().name())
                .orderId(p.getOrderId())
                .reservationId(p.getReservationId())
                .paymentId(p.getPaymentId())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
