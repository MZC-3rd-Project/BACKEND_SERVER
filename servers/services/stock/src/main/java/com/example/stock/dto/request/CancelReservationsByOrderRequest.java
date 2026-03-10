package com.example.stock.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CancelReservationsByOrderRequest {

    @NotNull
    private Long orderId;
}
