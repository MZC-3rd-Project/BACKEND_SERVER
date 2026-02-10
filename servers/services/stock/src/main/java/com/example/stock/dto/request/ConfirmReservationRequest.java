package com.example.stock.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ConfirmReservationRequest {

    @NotNull
    private Long reservationId;
}
