package com.example.analyticsdashboard.service.query;

import com.example.analyticsdashboard.dto.query.DashboardQueryMode;
import com.example.analyticsdashboard.dto.query.DashboardSeriesBucket;
import com.example.analyticsdashboard.dto.query.SellerDashboardOverviewQuery;
import com.example.analyticsdashboard.dto.response.DashboardLagStatus;
import com.example.analyticsdashboard.dto.response.SellerDashboardItemKpiResponse;
import com.example.analyticsdashboard.dto.response.SellerDashboardOverviewResponse;
import com.example.analyticsdashboard.dto.response.SellerDashboardQueryRangeResponse;
import com.example.analyticsdashboard.dto.response.SellerDashboardSalesKpiResponse;
import com.example.analyticsdashboard.dto.response.SellerDashboardSearchKpiResponse;
import com.example.analyticsdashboard.dto.response.SellerDashboardSeriesPointResponse;
import com.example.analyticsdashboard.exception.AnalyticsDashboardErrorCode;
import com.example.core.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class SellerDashboardOverviewQueryService {

    private static final String API_VERSION = "v1";

    public SellerDashboardOverviewResponse getOverview(Long sellerId, SellerDashboardOverviewQuery query) {
        return getOverviewByStore(sellerId, sellerId, query);
    }

    public SellerDashboardOverviewResponse getOverviewByStore(Long storeId,
                                                              Long sellerId,
                                                              SellerDashboardOverviewQuery query) {
        if (storeId == null || storeId <= 0) {
            throw new BusinessException(
                    AnalyticsDashboardErrorCode.INVALID_DASHBOARD_QUERY_PARAMETER,
                    "storeId는 1 이상이어야 합니다."
            );
        }

        SellerDashboardQueryRangeResponse queryRange = toQueryRange(query);
        List<SellerDashboardSeriesPointResponse> series = buildEmptySeries(query, queryRange);
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("funding", null);
        extensions.put("hotDeal", null);
        extensions.put("store", null);
        extensions.put("review", null);

        return SellerDashboardOverviewResponse.builder()
                .mode(query.mode())
                .queryRange(queryRange)
                .asOf(Instant.now())
                .lagStatus(DashboardLagStatus.HEALTHY)
                .partial(false)
                .sales(SellerDashboardSalesKpiResponse.empty())
                .item(SellerDashboardItemKpiResponse.empty())
                .search(SellerDashboardSearchKpiResponse.empty())
                .series(series)
                .apiVersion(API_VERSION)
                .extensions(extensions)
                .build();
    }

    private SellerDashboardQueryRangeResponse toQueryRange(SellerDashboardOverviewQuery query) {
        return switch (query.mode()) {
            case DAILY -> SellerDashboardQueryRangeResponse.builder()
                    .from(query.date().toString())
                    .to(query.date().toString())
                    .bucket(DashboardSeriesBucket.DAY.name())
                    .timezone(query.timezone().getId())
                    .build();
            case MONTHLY -> {
                LocalDate firstDay = query.yearMonth().atDay(1);
                LocalDate lastDay = query.yearMonth().atEndOfMonth();
                yield SellerDashboardQueryRangeResponse.builder()
                        .from(firstDay.toString())
                        .to(lastDay.toString())
                        .bucket(DashboardSeriesBucket.MONTH.name())
                        .timezone(query.timezone().getId())
                        .build();
            }
            case RANGE -> SellerDashboardQueryRangeResponse.builder()
                    .from(query.from().toString())
                    .to(query.to().toString())
                    .bucket(query.bucket().name())
                    .timezone(query.timezone().getId())
                    .build();
        };
    }

    private List<SellerDashboardSeriesPointResponse> buildEmptySeries(SellerDashboardOverviewQuery query,
                                                                      SellerDashboardQueryRangeResponse queryRange) {
        if (query.mode() == DashboardQueryMode.DAILY) {
            return List.of(emptyPoint(queryRange.getFrom()));
        }
        if (query.mode() == DashboardQueryMode.MONTHLY) {
            return List.of(emptyPoint(query.yearMonth().toString()));
        }
        if (query.bucket() == DashboardSeriesBucket.MONTH) {
            return buildMonthlySeries(query.from(), query.to());
        }
        return buildDailySeries(query.from(), query.to());
    }

    private List<SellerDashboardSeriesPointResponse> buildDailySeries(LocalDate from, LocalDate to) {
        List<SellerDashboardSeriesPointResponse> series = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            series.add(emptyPoint(cursor.toString()));
            cursor = cursor.plusDays(1);
        }
        return List.copyOf(series);
    }

    private List<SellerDashboardSeriesPointResponse> buildMonthlySeries(LocalDate from, LocalDate to) {
        List<SellerDashboardSeriesPointResponse> series = new ArrayList<>();
        YearMonth cursor = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);
        while (!cursor.isAfter(end)) {
            series.add(emptyPoint(cursor.toString()));
            cursor = cursor.plusMonths(1);
        }
        return List.copyOf(series);
    }

    private SellerDashboardSeriesPointResponse emptyPoint(String bucketStart) {
        return SellerDashboardSeriesPointResponse.builder()
                .bucketStart(bucketStart)
                .grossSales(0L)
                .netSales(0L)
                .orderCount(0L)
                .build();
    }
}
