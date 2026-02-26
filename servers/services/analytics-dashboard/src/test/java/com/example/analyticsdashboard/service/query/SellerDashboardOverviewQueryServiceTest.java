package com.example.analyticsdashboard.service.query;

import com.example.analyticsdashboard.dto.query.SellerDashboardOverviewQuery;
import com.example.analyticsdashboard.dto.response.SellerDashboardOverviewResponse;
import com.example.analyticsdashboard.exception.AnalyticsDashboardErrorCode;
import com.example.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SellerDashboardOverviewQueryServiceTest {

    private final SellerDashboardOverviewQueryService service = new SellerDashboardOverviewQueryService();

    @Test
    void getOverview_daily_returnsSingleSeriesAndDayRange() {
        SellerDashboardOverviewQuery query = SellerDashboardOverviewQuery.of(
                "DAILY",
                "2026-02-26",
                null,
                null,
                null,
                null,
                "Asia/Seoul"
        );

        SellerDashboardOverviewResponse response = service.getOverview(100L, query);

        assertThat(response.getMode().name()).isEqualTo("DAILY");
        assertThat(response.getQueryRange().getFrom()).isEqualTo("2026-02-26");
        assertThat(response.getQueryRange().getTo()).isEqualTo("2026-02-26");
        assertThat(response.getSeries()).hasSize(1);
        assertThat(response.getSeries().get(0).getBucketStart()).isEqualTo("2026-02-26");
    }

    @Test
    void getOverview_rangeDay_returnsSeriesForEachDate() {
        SellerDashboardOverviewQuery query = SellerDashboardOverviewQuery.of(
                "RANGE",
                null,
                null,
                "2026-02-01",
                "2026-02-03",
                "DAY",
                "UTC"
        );

        SellerDashboardOverviewResponse response = service.getOverview(10L, query);

        assertThat(response.getQueryRange().getBucket()).isEqualTo("DAY");
        assertThat(response.getSeries()).hasSize(3);
        assertThat(response.getSeries().get(0).getBucketStart()).isEqualTo("2026-02-01");
        assertThat(response.getSeries().get(2).getBucketStart()).isEqualTo("2026-02-03");
    }

    @Test
    void getOverview_throwsWhenSellerIdInvalid() {
        SellerDashboardOverviewQuery query = SellerDashboardOverviewQuery.of(
                "DAILY",
                "2026-02-26",
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.getOverview(0L, query))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AnalyticsDashboardErrorCode.INVALID_DASHBOARD_QUERY_PARAMETER);
                });
    }

    @Test
    void getOverviewByStore_throwsWhenStoreIdInvalid() {
        SellerDashboardOverviewQuery query = SellerDashboardOverviewQuery.of(
                "DAILY",
                "2026-02-26",
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.getOverviewByStore(-1L, 100L, query))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AnalyticsDashboardErrorCode.INVALID_DASHBOARD_QUERY_PARAMETER);
                });
    }
}
