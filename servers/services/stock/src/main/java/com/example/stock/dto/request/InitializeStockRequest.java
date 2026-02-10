package com.example.stock.dto.request;

import com.example.stock.entity.StockItemType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class InitializeStockRequest {

    @NotNull
    private Long itemId;

    @NotNull
    private StockItemType stockItemType;

    @NotNull
    private Long referenceId;

    @NotNull
    @Min(0)
    private Integer totalQuantity;
}
