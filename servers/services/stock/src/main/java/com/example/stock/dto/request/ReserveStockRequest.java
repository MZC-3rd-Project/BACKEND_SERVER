package com.example.stock.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReserveStockRequest {

    @NotNull
    private Long stockItemId;

    @NotNull
    private Long userId;

    @NotNull
    @Min(1)
    private Integer quantity;
}
