package com.example.stock.dto.request;

import com.example.stock.entity.StockItemType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ReserveOrderStockRequest {

    @NotBlank
    private String channelType;

    private Long channelRefId;

    @NotNull
    private Long userId;

    @NotBlank
    private String idempotencyKey;

    @Valid
    @NotEmpty
    private List<LineItem> lineItems;

    @Getter
    @NoArgsConstructor
    public static class LineItem {

        @NotNull
        private Long itemId;

        @NotNull
        private StockItemType stockItemType;

        @NotNull
        private Long referenceId;

        @NotNull
        @Min(1)
        private Integer quantity;
    }
}
