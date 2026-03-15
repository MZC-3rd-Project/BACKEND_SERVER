package com.example.cart.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CartCheckoutReservationResponse {

    private Long orderId;
    private LocalDateTime expiresAt;
    private List<ReservedLineItem> reservedItems;

    @Getter
    @Builder
    public static class ReservedLineItem {

        private Long itemId;
        private String stockItemType;
        private Long referenceId;
        private Integer quantity;
    }
}
