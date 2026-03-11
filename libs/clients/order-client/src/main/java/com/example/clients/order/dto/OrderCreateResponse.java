package com.example.clients.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreateResponse(
        Long orderId,
        String status
) {
}
