package com.example.analyticsdashboard.dto.response;

import com.example.analyticsdashboard.dto.query.DashboardQueryMode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class SellerDashboardFunnelResponse {

    private DashboardQueryMode mode;
    private SellerDashboardQueryRangeResponse queryRange;
    private Instant asOf;
    private DashboardLagStatus lagStatus;
    private boolean partial;
    private SellerDashboardFunnelDomainResponse sales;
    private SellerDashboardFunnelDomainResponse funding;
    private SellerDashboardFunnelDomainResponse hotDeal;
    private String apiVersion;
}
