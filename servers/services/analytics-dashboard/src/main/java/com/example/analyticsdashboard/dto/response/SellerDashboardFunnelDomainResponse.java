package com.example.analyticsdashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SellerDashboardFunnelDomainResponse {

    private String domainType;
    private long entryCount;
    private long conversionCount;
    private double conversionRate;
    private List<SellerDashboardFunnelStepResponse> steps;
}
