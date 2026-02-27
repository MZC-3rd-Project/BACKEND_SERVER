package com.example.analyticsdashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SellerDashboardItemKpiResponse {

    private Long onSaleCount;
    private Long soldOutCount;
    private Long hiddenCount;

    public static SellerDashboardItemKpiResponse empty() {
        return SellerDashboardItemKpiResponse.builder()
                .onSaleCount(0L)
                .soldOutCount(0L)
                .hiddenCount(0L)
                .build();
    }
}
