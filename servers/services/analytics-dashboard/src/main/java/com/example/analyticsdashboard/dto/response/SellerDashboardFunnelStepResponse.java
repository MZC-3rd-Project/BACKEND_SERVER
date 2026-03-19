package com.example.analyticsdashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SellerDashboardFunnelStepResponse {

    private String step;
    private long count;
}
