package com.example.stock.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class StockIncreaseRequest {

    @NotNull
    private Long stockItemId;

    @NotNull
    @Min(1)
    private Integer quantity;

    private String reason;
}
