package com.example.analyticsdashboard.service.query;

import com.example.analyticsdashboard.dto.query.DashboardQueryMode;
import com.example.analyticsdashboard.dto.query.DashboardSeriesBucket;
import com.example.analyticsdashboard.dto.query.SellerDashboardOverviewQuery;
import com.example.analyticsdashboard.dto.response.DashboardLagStatus;
import com.example.analyticsdashboard.dto.response.SellerDashboardFunnelResponse;
import com.example.analyticsdashboard.dto.response.SellerDashboardItemKpiResponse;
import com.example.analyticsdashboard.dto.response.SellerDashboardOverviewResponse;
import com.example.analyticsdashboard.dto.response.SellerDashboardQueryRangeResponse;
import com.example.analyticsdashboard.dto.response.SellerDashboardSalesKpiResponse;
import com.example.analyticsdashboard.dto.response.SellerDashboardSearchKpiResponse;
import com.example.analyticsdashboard.dto.response.SellerDashboardSeriesPointResponse;
import com.example.analyticsdashboard.entity.AnalyticsRawSalesEvent;
import com.example.analyticsdashboard.exception.AnalyticsDashboardErrorCode;
import com.example.analyticsdashboard.repository.AnalyticsDimItemSnapshotRepository;
import com.example.analyticsdashboard.repository.AnalyticsItemStatusCountRow;
import com.example.analyticsdashboard.repository.AnalyticsRawSalesEventRepository;
import com.example.analyticsdashboard.repository.AnalyticsRawSalesEventAggregateRow;
import com.example.analyticsdashboard.repository.AnalyticsRawSearchEventRepository;
import com.example.analyticsdashboard.repository.AnalyticsRawSearchEventCountRow;
import com.example.core.exception.BusinessException;
import com.example.data.entity.datasource.UseWriteDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@UseWriteDataSource
public class SellerDashboardOverviewQueryService {

    private static final String API_VERSION = "v1";
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

    private final AnalyticsRawSalesEventRepository rawSalesEventRepository;
    private final AnalyticsRawSearchEventRepository rawSearchEventRepository;
    private final AnalyticsDimItemSnapshotRepository dimItemSnapshotRepository;
    private final SellerDashboardFunnelQueryService sellerDashboardFunnelQueryService;

    public SellerDashboardOverviewResponse getOverview(Long sellerId, SellerDashboardOverviewQuery query) {
        validateSellerId(sellerId);

        QueryRangeContext queryRangeContext = resolveRange(query);
        List<AnalyticsRawSalesEvent> rawSalesEvents = rawSalesEventRepository.findBySellerIdAndOccurredAtBetween(
                sellerId,
                queryRangeContext.fromDateTime(),
                queryRangeContext.toDateTime()
        );
        List<AnalyticsRawSalesEventAggregateRow> salesAggregates =
                rawSalesEventRepository.aggregateBySellerIdAndOccurredAtBetween(
                        sellerId,
                        queryRangeContext.fromDateTime(),
                        queryRangeContext.toDateTime()
                );
        List<AnalyticsRawSearchEventCountRow> searchAggregates =
                rawSearchEventRepository.aggregateBySellerIdAndOccurredAtBetween(
                        sellerId,
                        queryRangeContext.fromDateTime(),
                        queryRangeContext.toDateTime()
                );
        List<AnalyticsItemStatusCountRow> itemStatusCounts = dimItemSnapshotRepository.countByStatusForSellerId(sellerId);
        LocalDateTime salesAsOf = rawSalesEventRepository.findLatestTimestampBySellerIdAndOccurredAtBetween(
                sellerId,
                queryRangeContext.fromDateTime(),
                queryRangeContext.toDateTime()
        );
        LocalDateTime searchAsOf = rawSearchEventRepository.findLatestTimestampBySellerIdAndOccurredAtBetween(
                sellerId,
                queryRangeContext.fromDateTime(),
                queryRangeContext.toDateTime()
        );

        return buildOverviewResponse(
                query,
                queryRangeContext,
                rawSalesEvents,
                salesAggregates,
                searchAggregates,
                itemStatusCounts,
                salesAsOf,
                searchAsOf,
                sellerDashboardFunnelQueryService.getFunnel(sellerId, query)
        );
    }

    public SellerDashboardOverviewResponse getOverviewByStore(Long storeId,
                                                              Long sellerId,
                                                              SellerDashboardOverviewQuery query) {
        validateStoreId(storeId);

        QueryRangeContext queryRangeContext = resolveRange(query);
        List<AnalyticsRawSalesEvent> rawSalesEvents;
        List<AnalyticsRawSalesEventAggregateRow> salesAggregates;
        List<AnalyticsRawSearchEventCountRow> searchAggregates;
        List<AnalyticsItemStatusCountRow> itemStatusCounts;
        LocalDateTime salesAsOf;
        LocalDateTime searchAsOf;

        if (hasPositiveId(sellerId)) {
            rawSalesEvents = rawSalesEventRepository.findByStoreIdAndSellerIdAndOccurredAtBetween(
                    storeId,
                    sellerId,
                    queryRangeContext.fromDateTime(),
                    queryRangeContext.toDateTime()
            );
            salesAggregates = rawSalesEventRepository.aggregateByStoreIdAndSellerIdAndOccurredAtBetween(
                    storeId,
                    sellerId,
                    queryRangeContext.fromDateTime(),
                    queryRangeContext.toDateTime()
            );
            searchAggregates = rawSearchEventRepository.aggregateByStoreIdAndSellerIdAndOccurredAtBetween(
                    storeId,
                    sellerId,
                    queryRangeContext.fromDateTime(),
                    queryRangeContext.toDateTime()
            );
            itemStatusCounts = dimItemSnapshotRepository.countByStatusForStoreIdAndSellerId(storeId, sellerId);
            salesAsOf = rawSalesEventRepository.findLatestTimestampByStoreIdAndSellerIdAndOccurredAtBetween(
                    storeId,
                    sellerId,
                    queryRangeContext.fromDateTime(),
                    queryRangeContext.toDateTime()
            );
            searchAsOf = rawSearchEventRepository.findLatestTimestampByStoreIdAndSellerIdAndOccurredAtBetween(
                    storeId,
                    sellerId,
                    queryRangeContext.fromDateTime(),
                    queryRangeContext.toDateTime()
            );
        } else {
            rawSalesEvents = rawSalesEventRepository.findByStoreIdAndOccurredAtBetween(
                    storeId,
                    queryRangeContext.fromDateTime(),
                    queryRangeContext.toDateTime()
            );
            salesAggregates = rawSalesEventRepository.aggregateByStoreIdAndOccurredAtBetween(
                    storeId,
                    queryRangeContext.fromDateTime(),
                    queryRangeContext.toDateTime()
            );
            searchAggregates = rawSearchEventRepository.aggregateByStoreIdAndOccurredAtBetween(
                    storeId,
                    queryRangeContext.fromDateTime(),
                    queryRangeContext.toDateTime()
            );
            itemStatusCounts = dimItemSnapshotRepository.countByStatusForStoreId(storeId);
            salesAsOf = rawSalesEventRepository.findLatestTimestampByStoreIdAndOccurredAtBetween(
                    storeId,
                    queryRangeContext.fromDateTime(),
                    queryRangeContext.toDateTime()
            );
            searchAsOf = rawSearchEventRepository.findLatestTimestampByStoreIdAndOccurredAtBetween(
                    storeId,
                    queryRangeContext.fromDateTime(),
                    queryRangeContext.toDateTime()
            );
        }

        return buildOverviewResponse(
                query,
                queryRangeContext,
                rawSalesEvents,
                salesAggregates,
                searchAggregates,
                itemStatusCounts,
                salesAsOf,
                searchAsOf,
                sellerDashboardFunnelQueryService.getFunnelByStore(storeId, sellerId, query)
        );
    }

    private SellerDashboardOverviewResponse buildOverviewResponse(SellerDashboardOverviewQuery query,
                                                                  QueryRangeContext queryRangeContext,
                                                                  List<AnalyticsRawSalesEvent> rawSalesEvents,
                                                                  List<AnalyticsRawSalesEventAggregateRow> salesAggregates,
                                                                  List<AnalyticsRawSearchEventCountRow> searchAggregates,
                                                                  List<AnalyticsItemStatusCountRow> itemStatusCounts,
                                                                  LocalDateTime salesAsOf,
                                                                  LocalDateTime searchAsOf,
                                                                  SellerDashboardFunnelResponse funnel) {
        SellerDashboardQueryRangeResponse queryRange = toQueryRange(queryRangeContext, query.timezone().getId());
        SellerDashboardSalesKpiResponse sales = toSalesKpi(salesAggregates);
        SellerDashboardSearchKpiResponse search = toSearchKpi(searchAggregates);
        SellerDashboardItemKpiResponse item = toItemKpi(itemStatusCounts);
        List<SellerDashboardSeriesPointResponse> series = toSeries(rawSalesEvents, queryRangeContext);
        Instant asOf = resolveAsOf(salesAsOf, searchAsOf);

        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("funding", null);
        extensions.put("hotDeal", null);
        extensions.put("store", null);
        extensions.put("review", null);
        extensions.put("funnel", funnel);

        return SellerDashboardOverviewResponse.builder()
                .mode(query.mode())
                .queryRange(queryRange)
                .asOf(resolveAsOf(asOf, funnel))
                .lagStatus(resolveLagStatus(funnel))
                .partial(resolvePartial(funnel))
                .sales(sales)
                .item(item)
                .search(search)
                .series(series)
                .apiVersion(API_VERSION)
                .extensions(extensions)
                .build();
    }

    private Instant resolveAsOf(Instant baseAsOf, SellerDashboardFunnelResponse funnel) {
        if (funnel == null || funnel.getAsOf() == null) {
            return baseAsOf;
        }
        if (baseAsOf == null || funnel.getAsOf().isAfter(baseAsOf)) {
            return funnel.getAsOf();
        }
        return baseAsOf;
    }

    private DashboardLagStatus resolveLagStatus(SellerDashboardFunnelResponse funnel) {
        if (funnel == null || funnel.getLagStatus() == null) {
            return DashboardLagStatus.HEALTHY;
        }
        return funnel.getLagStatus();
    }

    private boolean resolvePartial(SellerDashboardFunnelResponse funnel) {
        return funnel != null && funnel.isPartial();
    }

    private void validateStoreId(Long storeId) {
        if (!hasPositiveId(storeId)) {
            throw new BusinessException(
                    AnalyticsDashboardErrorCode.INVALID_DASHBOARD_QUERY_PARAMETER,
                    "storeId는 1 이상이어야 합니다."
            );
        }
    }

    private void validateSellerId(Long sellerId) {
        if (!hasPositiveId(sellerId)) {
            throw new BusinessException(
                    AnalyticsDashboardErrorCode.INVALID_DASHBOARD_QUERY_PARAMETER,
                    "sellerId는 1 이상이어야 합니다."
            );
        }
    }

    private boolean hasPositiveId(Long value) {
        return value != null && value > 0;
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

    private SellerDashboardSalesKpiResponse toSalesKpi(List<AnalyticsRawSalesEventAggregateRow> aggregateRows) {
        Map<String, AnalyticsRawSalesEventAggregateRow> aggregateMap = indexSalesAggregates(aggregateRows);
        long grossSales = sumSalesField(aggregateRows, AnalyticsRawSalesEventAggregateRow::grossAmountSum);
        long netSales = sumSalesField(aggregateRows, AnalyticsRawSalesEventAggregateRow::netAmountSum);
        long orderCount = countSalesEvents(aggregateMap, "PURCHASE_CREATED");
        long cancelCount = countSalesEvents(aggregateMap, "PURCHASE_CANCELLED");
        long refundCount = aggregateRows.stream()
                .filter(row -> row.eventType() != null && row.eventType().contains("REFUND"))
                .mapToLong(row -> nullSafeLong(row.eventCount()))
                .sum();

        return SellerDashboardSalesKpiResponse.builder()
                .grossSales(grossSales)
                .netSales(netSales)
                .orderCount(orderCount)
                .cancelCount(cancelCount)
                .refundCount(refundCount)
                .build();
    }

    private SellerDashboardSearchKpiResponse toSearchKpi(List<AnalyticsRawSearchEventCountRow> aggregateRows) {
        Map<String, Long> aggregateMap = indexSearchAggregates(aggregateRows);
        long searchCount = aggregateMap.getOrDefault("SEARCH_EXECUTED", 0L);
        long clickCount = aggregateMap.getOrDefault("SEARCH_ITEM_CLICKED", 0L);

        double ctr = searchCount == 0 ? 0.0d : (double) clickCount / (double) searchCount;
        return SellerDashboardSearchKpiResponse.builder()
                .searchCount(searchCount)
                .clickCount(clickCount)
                .ctr(ctr)
                .build();
    }

    private SellerDashboardItemKpiResponse toItemKpi(List<AnalyticsItemStatusCountRow> statusCounts) {
        Map<String, Long> statusCountMap = indexItemStatusCounts(statusCounts);
        long onSaleCount = statusCountMap.getOrDefault("ON_SALE", 0L);
        long soldOutCount = statusCountMap.getOrDefault("SOLD_OUT", 0L);
        long hiddenCount = statusCountMap.getOrDefault("HIDDEN", 0L);

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

    private Map<String, AnalyticsRawSalesEventAggregateRow> indexSalesAggregates(
            List<AnalyticsRawSalesEventAggregateRow> aggregateRows
    ) {
        Map<String, AnalyticsRawSalesEventAggregateRow> aggregateMap = new HashMap<>();
        for (AnalyticsRawSalesEventAggregateRow aggregateRow : aggregateRows) {
            aggregateMap.put(normalize(aggregateRow.eventType()), aggregateRow);
        }
        return aggregateMap;
    }

    private Map<String, Long> indexSearchAggregates(List<AnalyticsRawSearchEventCountRow> aggregateRows) {
        Map<String, Long> aggregateMap = new HashMap<>();
        for (AnalyticsRawSearchEventCountRow aggregateRow : aggregateRows) {
            aggregateMap.put(normalize(aggregateRow.eventType()), nullSafeLong(aggregateRow.eventCount()));
        }
        return aggregateMap;
    }

    private Map<String, Long> indexItemStatusCounts(List<AnalyticsItemStatusCountRow> statusCounts) {
        Map<String, Long> statusMap = new HashMap<>();
        for (AnalyticsItemStatusCountRow statusCount : statusCounts) {
            statusMap.put(normalize(statusCount.itemStatus()), nullSafeLong(statusCount.itemCount()));
        }
        return statusMap;
    }

    private long sumSalesField(
            List<AnalyticsRawSalesEventAggregateRow> aggregateRows,
            java.util.function.Function<AnalyticsRawSalesEventAggregateRow, Long> extractor
    ) {
        return aggregateRows.stream()
                .map(extractor)
                .mapToLong(this::nullSafeLong)
                .sum();
    }

    private long countSalesEvents(Map<String, AnalyticsRawSalesEventAggregateRow> aggregateMap, String eventType) {
        AnalyticsRawSalesEventAggregateRow aggregate = aggregateMap.get(eventType);
        return aggregate == null ? 0L : nullSafeLong(aggregate.eventCount());
    }

    private Instant resolveAsOf(LocalDateTime salesAsOf, LocalDateTime searchAsOf) {
        LocalDateTime latest = salesAsOf;
        if (latest == null || (searchAsOf != null && searchAsOf.isAfter(latest))) {
            latest = searchAsOf;
        }
        if (latest == null) {
            latest = LocalDateTime.now();
        }
        return latest.atZone(DEFAULT_ZONE).toInstant();
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
