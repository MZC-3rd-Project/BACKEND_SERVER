package com.example.analyticsdashboard.dto.query;

import com.example.analyticsdashboard.exception.AnalyticsDashboardErrorCode;
import com.example.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SellerDashboardOverviewQueryTest {

    @Test
    void of_dailyMode_parsesDateAndDefaultTimezone() {
        SellerDashboardOverviewQuery query = SellerDashboardOverviewQuery.of(
                "DAILY",
                "2026-02-26",
                null,
                null,
                null,
                null,
                null
        );

        assertThat(query.mode()).isEqualTo(DashboardQueryMode.DAILY);
        assertThat(query.date()).isEqualTo(LocalDate.of(2026, 2, 26));
        assertThat(query.timezone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Test
    void of_monthlyMode_parsesYearMonth() {
        SellerDashboardOverviewQuery query = SellerDashboardOverviewQuery.of(
                "MONTHLY",
                null,
                "2026-02",
                null,
                null,
                null,
                "UTC"
        );

        assertThat(query.mode()).isEqualTo(DashboardQueryMode.MONTHLY);
        assertThat(query.yearMonth()).isEqualTo(YearMonth.of(2026, 2));
        assertThat(query.timezone()).isEqualTo(ZoneId.of("UTC"));
    }

    @Test
    void of_rangeMode_usesDefaultBucketDay() {
        SellerDashboardOverviewQuery query = SellerDashboardOverviewQuery.of(
                "RANGE",
                null,
                null,
                "2026-02-01",
                "2026-02-10",
                null,
                "Asia/Seoul"
        );

        assertThat(query.mode()).isEqualTo(DashboardQueryMode.RANGE);
        assertThat(query.from()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(query.to()).isEqualTo(LocalDate.of(2026, 2, 10));
        assertThat(query.bucket()).isEqualTo(DashboardSeriesBucket.DAY);
    }

    @Test
    void of_throwsWhenDailyDateMissing() {
        assertThatThrownBy(() -> SellerDashboardOverviewQuery.of(
                "DAILY",
                null,
                null,
                null,
                null,
                null,
                null
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AnalyticsDashboardErrorCode.INVALID_DASHBOARD_QUERY_PARAMETER);
                });
    }

    @Test
    void of_throwsWhenRangeFromAfterTo() {
        assertThatThrownBy(() -> SellerDashboardOverviewQuery.of(
                "RANGE",
                null,
                null,
                "2026-02-27",
                "2026-02-26",
                "DAY",
                null
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AnalyticsDashboardErrorCode.INVALID_DASHBOARD_QUERY_PARAMETER);
                });
    }

    @Test
    void of_throwsWhenRangeExceedsMaxDays() {
        assertThatThrownBy(() -> SellerDashboardOverviewQuery.of(
                "RANGE",
                null,
                null,
                "2026-01-01",
                "2026-04-10",
                "DAY",
                null
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AnalyticsDashboardErrorCode.INVALID_DASHBOARD_QUERY_PARAMETER);
                });
    }

    @Test
    void of_throwsWhenTimezoneInvalid() {
        assertThatThrownBy(() -> SellerDashboardOverviewQuery.of(
                "DAILY",
                "2026-02-26",
                null,
                null,
                null,
                null,
                "Asia/Unknown"
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AnalyticsDashboardErrorCode.INVALID_DASHBOARD_QUERY_PARAMETER);
                });
    }
}
