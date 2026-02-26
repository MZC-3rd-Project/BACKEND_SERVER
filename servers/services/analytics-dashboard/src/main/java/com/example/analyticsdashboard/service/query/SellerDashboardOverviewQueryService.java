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
import com.example.analyticsdashboard.entity.AnalyticsDimItemSnapshot;
import com.example.analyticsdashboard.entity.AnalyticsRawSalesEvent;
import com.example.analyticsdashboard.entity.AnalyticsRawSearchEvent;
import com.example.analyticsdashboard.exception.AnalyticsDashboardErrorCode;
import com.example.analyticsdashboard.repository.AnalyticsDimItemSnapshotRepository;
import com.example.analyticsdashboard.repository.AnalyticsRawSalesEventRepository;
import com.example.analyticsdashboard.repository.AnalyticsRawSearchEventRepository;
import com.example.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerDashboardOverviewQueryService {

    private static final String API_VERSION = "v1";

    private final AnalyticsRawSalesEventRepository rawSalesEventRepository;
    private final AnalyticsRawSearchEventRepository rawSearchEventRepository;
    private final AnalyticsDimItemSnapshotRepository dimItemSnapshotRepository;

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

        QueryRangeContext queryRangeContext = resolveRange(query);
        SellerDashboardQueryRangeResponse queryRange = toQueryRange(queryRangeContext, query.timezone().getId());

        List<AnalyticsRawSalesEvent> rawSalesEvents = rawSalesEventRepository.findByStoreIdAndOccurredAtBetween(
                storeId,
                queryRangeContext.fromDateTime(),
                queryRangeContext.toDateTime()
        );
        List<AnalyticsRawSearchEvent> rawSearchEvents = rawSearchEventRepository.findByStoreIdAndOccurredAtBetween(
                storeId,
                queryRangeContext.fromDateTime(),
                queryRangeContext.toDateTime()
        );
        List<AnalyticsDimItemSnapshot> itemSnapshots = dimItemSnapshotRepository.findByStoreId(storeId);

        SellerDashboardSalesKpiResponse sales = toSalesKpi(rawSalesEvents);
        SellerDashboardSearchKpiResponse search = toSearchKpi(rawSearchEvents);
        SellerDashboardItemKpiResponse item = toItemKpi(itemSnapshots);
        List<SellerDashboardSeriesPointResponse> series = toSeries(rawSalesEvents, queryRangeContext);
        Instant asOf = resolveAsOf(rawSalesEvents, rawSearchEvents);

        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("funding", null);
        extensions.put("hotDeal", null);
        extensions.put("store", null);
        extensions.put("review", null);

        return SellerDashboardOverviewResponse.builder()
                .mode(query.mode())
                .queryRange(queryRange)
                .asOf(asOf)
                .lagStatus(DashboardLagStatus.HEALTHY)
                .partial(false)
                .sales(sales)
                .item(item)
                .search(search)
                .series(series)
                .apiVersion(API_VERSION)
                .extensions(extensions)
                .build();
    }

    private SellerDashboardQueryRangeResponse toQueryRange(QueryRangeContext queryRangeContext, String timezone) {
        return SellerDashboardQueryRangeResponse.builder()
                .from(queryRangeContext.fromDate().toString())
                .to(queryRangeContext.toDate().toString())
                .bucket(queryRangeContext.bucket().name())
                .timezone(timezone)
                .build();
    }

    private QueryRangeContext resolveRange(SellerDashboardOverviewQuery query) {
        if (query.mode() == DashboardQueryMode.DAILY) {
            LocalDate date = query.date();
            return new QueryRangeContext(
                    date,
                    date,
                    date.atStartOfDay(),
                    date.atTime(23, 59, 59),
                    DashboardSeriesBucket.DAY
            );
        }
        if (query.mode() == DashboardQueryMode.MONTHLY) {
            LocalDate from = query.yearMonth().atDay(1);
            LocalDate to = query.yearMonth().atEndOfMonth();
            return new QueryRangeContext(
                    from,
                    to,
                    from.atStartOfDay(),
                    to.atTime(23, 59, 59),
                    DashboardSeriesBucket.MONTH
            );
        }
        return new QueryRangeContext(
                query.from(),
                query.to(),
                query.from().atStartOfDay(),
                query.to().atTime(23, 59, 59),
                query.bucket()
        );
    }

    private SellerDashboardSalesKpiResponse toSalesKpi(List<AnalyticsRawSalesEvent> rawSalesEvents) {
        long grossSales = 0L;
        long netSales = 0L;
        long orderCount = 0L;
        long cancelCount = 0L;
        long refundCount = 0L;

        for (AnalyticsRawSalesEvent event : rawSalesEvents) {
            grossSales += nullSafeLong(event.getGrossAmount());
            netSales += nullSafeLong(event.getNetAmount());

            String eventType = normalize(event.getEventType());
            if ("PURCHASE_CREATED".equals(eventType)) {
                orderCount++;
            } else if ("PURCHASE_CANCELLED".equals(eventType)) {
                cancelCount++;
            } else if (eventType.contains("REFUND")) {
                refundCount++;
            }
        }

        return SellerDashboardSalesKpiResponse.builder()
                .grossSales(grossSales)
                .netSales(netSales)
                .orderCount(orderCount)
                .cancelCount(cancelCount)
                .refundCount(refundCount)
                .build();
    }

    private SellerDashboardSearchKpiResponse toSearchKpi(List<AnalyticsRawSearchEvent> rawSearchEvents) {
        long searchCount = 0L;
        long clickCount = 0L;

        for (AnalyticsRawSearchEvent event : rawSearchEvents) {
            String eventType = normalize(event.getEventType());
            if ("SEARCH_EXECUTED".equals(eventType)) {
                searchCount++;
            } else if ("SEARCH_ITEM_CLICKED".equals(eventType)) {
                clickCount++;
            }
        }

        double ctr = searchCount == 0 ? 0.0d : (double) clickCount / (double) searchCount;
        return SellerDashboardSearchKpiResponse.builder()
                .searchCount(searchCount)
                .clickCount(clickCount)
                .ctr(ctr)
                .build();
    }

    private SellerDashboardItemKpiResponse toItemKpi(List<AnalyticsDimItemSnapshot> itemSnapshots) {
        long onSaleCount = 0L;
        long soldOutCount = 0L;
        long hiddenCount = 0L;

        for (AnalyticsDimItemSnapshot snapshot : itemSnapshots) {
            String status = normalize(snapshot.getItemStatus());
            if ("ON_SALE".equals(status)) {
                onSaleCount++;
            } else if ("SOLD_OUT".equals(status)) {
                soldOutCount++;
            } else if ("HIDDEN".equals(status)) {
                hiddenCount++;
            }
        }

        return SellerDashboardItemKpiResponse.builder()
                .onSaleCount(onSaleCount)
                .soldOutCount(soldOutCount)
                .hiddenCount(hiddenCount)
                .build();
    }

    private List<SellerDashboardSeriesPointResponse> toSeries(List<AnalyticsRawSalesEvent> rawSalesEvents,
                                                              QueryRangeContext queryRangeContext) {
        Map<String, BucketSalesAggregate> aggregateMap = aggregateByBucket(rawSalesEvents, queryRangeContext.bucket());
        List<String> buckets = buildBucketTimeline(queryRangeContext.fromDate(), queryRangeContext.toDate(), queryRangeContext.bucket());
        List<SellerDashboardSeriesPointResponse> series = new ArrayList<>();
        for (String bucket : buckets) {
            BucketSalesAggregate aggregate = aggregateMap.getOrDefault(bucket, BucketSalesAggregate.empty());
            series.add(SellerDashboardSeriesPointResponse.builder()
                    .bucketStart(bucket)
                    .grossSales(aggregate.grossSales())
                    .netSales(aggregate.netSales())
                    .orderCount(aggregate.orderCount())
                    .build());
        }
        return List.copyOf(series);
    }

    private Map<String, BucketSalesAggregate> aggregateByBucket(List<AnalyticsRawSalesEvent> rawSalesEvents,
                                                                DashboardSeriesBucket bucket) {
        Map<String, BucketSalesAggregate> aggregates = new HashMap<>();
        for (AnalyticsRawSalesEvent event : rawSalesEvents) {
            if (event.getOccurredAt() == null) {
                continue;
            }
            String bucketKey = bucket == DashboardSeriesBucket.MONTH
                    ? YearMonth.from(event.getOccurredAt()).toString()
                    : event.getOccurredAt().toLocalDate().toString();

            BucketSalesAggregate current = aggregates.getOrDefault(bucketKey, BucketSalesAggregate.empty());
            long grossSales = current.grossSales() + nullSafeLong(event.getGrossAmount());
            long netSales = current.netSales() + nullSafeLong(event.getNetAmount());
            long orderCount = current.orderCount() + ("PURCHASE_CREATED".equals(normalize(event.getEventType())) ? 1L : 0L);
            aggregates.put(bucketKey, new BucketSalesAggregate(grossSales, netSales, orderCount));
        }
        return aggregates;
    }

    private List<String> buildBucketTimeline(LocalDate from, LocalDate to, DashboardSeriesBucket bucket) {
        List<String> buckets = new ArrayList<>();
        if (bucket == DashboardSeriesBucket.MONTH) {
            YearMonth cursor = YearMonth.from(from);
            YearMonth end = YearMonth.from(to);
            while (!cursor.isAfter(end)) {
                buckets.add(cursor.toString());
                cursor = cursor.plusMonths(1);
            }
            return List.copyOf(buckets);
        }

        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            buckets.add(cursor.toString());
            cursor = cursor.plusDays(1);
        }
        return List.copyOf(buckets);
    }

    private Instant resolveAsOf(List<AnalyticsRawSalesEvent> salesEvents,
                                List<AnalyticsRawSearchEvent> searchEvents) {
        LocalDateTime latest = Stream.concat(
                        salesEvents.stream().map(AnalyticsRawSalesEvent::getIngestedAt),
                        searchEvents.stream().map(AnalyticsRawSearchEvent::getIngestedAt)
                )
                .filter(value -> value != null)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
        return latest.atZone(java.time.ZoneId.of("Asia/Seoul")).toInstant();
    }

    private long nullSafeLong(Long value) {
        return value == null ? 0L : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private record QueryRangeContext(
            LocalDate fromDate,
            LocalDate toDate,
            LocalDateTime fromDateTime,
            LocalDateTime toDateTime,
            DashboardSeriesBucket bucket
    ) {
    }

    private record BucketSalesAggregate(long grossSales, long netSales, long orderCount) {
        private static BucketSalesAggregate empty() {
            return new BucketSalesAggregate(0L, 0L, 0L);
        }
    }
}
