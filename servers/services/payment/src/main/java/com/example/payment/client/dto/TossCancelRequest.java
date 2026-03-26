package com.example.payment.client.dto;

public record TossCancelRequest(
        String cancelReason,
        Long cancelAmount
) {
}
