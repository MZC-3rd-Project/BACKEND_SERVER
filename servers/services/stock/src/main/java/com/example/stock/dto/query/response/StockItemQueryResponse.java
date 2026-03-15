package com.example.stock.dto.query.response;

import com.example.stock.entity.StockItemType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockItemQueryResponse {

    private Long id;
    private Long itemId;
    private StockItemType stockItemType;
    private Long referenceId;
    private int totalQuantity;
    private int availableQuantity;
    private int reservedQuantity;
}
