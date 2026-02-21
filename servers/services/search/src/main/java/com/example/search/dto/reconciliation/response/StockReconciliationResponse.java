package com.example.search.dto.reconciliation.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockReconciliationResponse {

    private final Long itemId;
    private final Integer sourceAvailableStock;
    private final Integer indexedStock;
    private final boolean matched;
    private final String result;
}
