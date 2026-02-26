package com.example.analyticsdashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SellerDashboardSalesKpiResponse {

    private Long grossSales;
    private Long netSales;
    private Long orderCount;
    private Long cancelCount;
    private Long refundCount;

    public static SellerDashboardSalesKpiResponse empty() {
        return SellerDashboardSalesKpiResponse.builder()
                .grossSales(0L)
                .netSales(0L)
                .orderCount(0L)
                .cancelCount(0L)
                .refundCount(0L)
                .build();
    }
}
