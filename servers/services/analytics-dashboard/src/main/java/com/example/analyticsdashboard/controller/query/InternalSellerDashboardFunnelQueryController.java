package com.example.analyticsdashboard.controller.query;

import com.example.analyticsdashboard.dto.query.SellerDashboardOverviewQuery;
import com.example.analyticsdashboard.dto.response.SellerDashboardFunnelResponse;
import com.example.analyticsdashboard.service.query.SellerDashboardFunnelQueryService;
import com.example.api.response.ApiResponse;
import com.example.contracts.http.HttpHeaderNames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Internal", description = "셀러 대시보드 퍼널 내부 조회 API")
@RestController
@RequestMapping("/internal/v1/analytics")
@RequiredArgsConstructor
public class InternalSellerDashboardFunnelQueryController {

    private final SellerDashboardFunnelQueryService sellerDashboardFunnelQueryService;

    @Operation(summary = "셀러 대시보드 funnel 조회 (내부)")
    @GetMapping("/sellers/{sellerId}/dashboard/funnel")
    public ApiResponse<SellerDashboardFunnelResponse> getFunnel(
            @PathVariable Long sellerId,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String yearMonth,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String bucket,
            @RequestParam(required = false) String timezone
    ) {
        SellerDashboardOverviewQuery query = SellerDashboardOverviewQuery.of(
                mode,
                date,
                yearMonth,
                from,
                to,
                bucket,
                timezone
        );
        return ApiResponse.success(sellerDashboardFunnelQueryService.getFunnel(sellerId, query));
    }

    @Operation(summary = "스토어 대시보드 funnel 조회 (내부)")
    @GetMapping("/stores/{storeId}/dashboard/funnel")
    public ApiResponse<SellerDashboardFunnelResponse> getFunnelByStore(
            @PathVariable Long storeId,
            @RequestHeader(value = HttpHeaderNames.USER_ID, required = false) Long sellerId,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String yearMonth,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String bucket,
            @RequestParam(required = false) String timezone
    ) {
        SellerDashboardOverviewQuery query = SellerDashboardOverviewQuery.of(
                mode,
                date,
                yearMonth,
                from,
                to,
                bucket,
                timezone
        );
        return ApiResponse.success(sellerDashboardFunnelQueryService.getFunnelByStore(storeId, sellerId, query));
    }
}
