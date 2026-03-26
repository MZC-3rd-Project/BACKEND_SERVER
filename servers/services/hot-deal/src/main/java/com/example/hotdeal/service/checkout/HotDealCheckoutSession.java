package com.example.hotdeal.service.checkout;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HotDealCheckoutSession {

    private Long orderId;
    private Long hotDealId;
    private Long itemId;
    private Long userId;
    private Long sellerId;
    private Long storeId;
    private String itemType;
    private String title;
    private Integer quantity;
    private Long unitPrice;
    private Long totalAmount;
    private String idempotencyKey;
    private LocalDateTime expiresAt;
    private LocalDateTime orderCreatedAt;
    private HotDealCheckoutSessionStatus status;

    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && (expiresAt.isBefore(now) || expiresAt.isEqual(now));
    }

    public boolean isTerminal() {
        return status == HotDealCheckoutSessionStatus.CONFIRMED
                || status == HotDealCheckoutSessionStatus.CANCELLED
                || status == HotDealCheckoutSessionStatus.EXPIRED;
    }

    public HotDealCheckoutSession markOrderCreated(LocalDateTime when) {
        return toBuilder()
                .status(HotDealCheckoutSessionStatus.ORDER_CREATED)
                .orderCreatedAt(when)
                .build();
    }

    public HotDealCheckoutSession markConfirmed() {
        return toBuilder()
                .status(HotDealCheckoutSessionStatus.CONFIRMED)
                .build();
    }

    public HotDealCheckoutSession markCancelled() {
        return toBuilder()
                .status(HotDealCheckoutSessionStatus.CANCELLED)
                .build();
    }

    public HotDealCheckoutSession markExpired() {
        return toBuilder()
                .status(HotDealCheckoutSessionStatus.EXPIRED)
                .build();
    }
}
