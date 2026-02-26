package com.example.analyticsdashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SellerDashboardSeriesPointResponse {

    private String bucketStart;
    private Long grossSales;
    private Long netSales;
    private Long orderCount;
}
