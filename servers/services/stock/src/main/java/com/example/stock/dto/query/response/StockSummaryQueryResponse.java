package com.example.stock.dto.query.response;

import com.example.stock.entity.StockItemType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class StockSummaryQueryResponse {

    private Long itemId;
    private List<StockDetail> stocks;

    @Getter
    @Builder
    public static class StockDetail {
        private Long stockItemId;
        private StockItemType stockItemType;
        private Long referenceId;
        private int totalQuantity;
        private int availableQuantity;
        private int reservedQuantity;
    }
}
