package com.example.sales.dto.checkout.response;

import com.example.core.id.jackson.SnowflakeId;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class CheckoutReserveResponse {

    @SnowflakeId
    private Long orderId;

    private LocalDateTime expiresAt;

    private List<ReservedLineItem> reservedItems;

    @Getter
    @Builder
    public static class ReservedLineItem {

        @SnowflakeId
        private Long itemId;

        private String stockItemType;

        @SnowflakeId
        private Long referenceId;

        private Integer quantity;
    }
}
