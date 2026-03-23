package com.example.analyticsdashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class SellerDashboardReviewKpiResponse {

    private Long reviewCount;
    private Long reviewedItemCount;
    private BigDecimal averageRating;

    public static SellerDashboardReviewKpiResponse empty() {
        return SellerDashboardReviewKpiResponse.builder()
                .reviewCount(0L)
                .reviewedItemCount(0L)
                .averageRating(BigDecimal.ZERO.setScale(2))
                .build();
    }
}
