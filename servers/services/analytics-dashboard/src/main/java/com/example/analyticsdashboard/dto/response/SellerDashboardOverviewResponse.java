package com.example.analyticsdashboard.dto.response;

import com.example.analyticsdashboard.dto.query.DashboardQueryMode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class SellerDashboardOverviewResponse {

    private DashboardQueryMode mode;
    private SellerDashboardQueryRangeResponse queryRange;
    private Instant asOf;
    private DashboardLagStatus lagStatus;
    private boolean partial;
    private SellerDashboardSalesKpiResponse sales;
    private SellerDashboardItemKpiResponse item;
    private SellerDashboardSearchKpiResponse search;
    private List<SellerDashboardSeriesPointResponse> series;
    private String apiVersion;
    private Map<String, Object> extensions;
}
