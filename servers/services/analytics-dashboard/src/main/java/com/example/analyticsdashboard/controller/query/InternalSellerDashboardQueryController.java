package com.example.analyticsdashboard.controller.query;

import com.example.analyticsdashboard.dto.query.SellerDashboardOverviewQuery;
import com.example.analyticsdashboard.dto.response.SellerDashboardOverviewResponse;
import com.example.analyticsdashboard.service.query.SellerDashboardOverviewQueryService;
import com.example.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Internal", description = "셀러 대시보드 내부 조회 API")
@RestController
@RequestMapping("/internal/v1/analytics/sellers")
@RequiredArgsConstructor
public class InternalSellerDashboardQueryController {

    private final SellerDashboardOverviewQueryService sellerDashboardOverviewQueryService;

    @Operation(summary = "셀러 대시보드 overview 조회 (내부)")
    @GetMapping("/{sellerId}/dashboard/overview")
    public ApiResponse<SellerDashboardOverviewResponse> getOverview(
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
        return ApiResponse.success(sellerDashboardOverviewQueryService.getOverview(sellerId, query));
    }
}
