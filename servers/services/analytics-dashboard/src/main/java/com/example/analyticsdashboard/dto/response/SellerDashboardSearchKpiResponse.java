package com.example.analyticsdashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SellerDashboardSearchKpiResponse {

    private Long searchCount;
    private Long clickCount;
    private Double ctr;

    public static SellerDashboardSearchKpiResponse empty() {
        return SellerDashboardSearchKpiResponse.builder()
                .searchCount(0L)
                .clickCount(0L)
                .ctr(0.0d)
                .build();
    }
}
