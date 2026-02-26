package com.example.analyticsdashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SellerDashboardQueryRangeResponse {

    private String from;
    private String to;
    private String bucket;
    private String timezone;
}
