package com.example.analyticsdashboard.service.query;

import com.example.analyticsdashboard.dto.query.DashboardQueryMode;
import com.example.analyticsdashboard.dto.query.DashboardSeriesBucket;
import com.example.analyticsdashboard.dto.query.SellerDashboardOverviewQuery;
import com.example.analyticsdashboard.dto.response.DashboardLagStatus;
import com.example.analyticsdashboard.dto.response.SellerDashboardFunnelDomainResponse;
import com.example.analyticsdashboard.dto.response.SellerDashboardFunnelResponse;
import com.example.analyticsdashboard.dto.response.SellerDashboardFunnelStepResponse;
import com.example.analyticsdashboard.dto.response.SellerDashboardQueryRangeResponse;
import com.example.analyticsdashboard.repository.AnalyticsJourneyEventCountRow;
import com.example.analyticsdashboard.exception.AnalyticsDashboardErrorCode;
import com.example.analyticsdashboard.repository.AnalyticsJourneyEventRepository;
import com.example.core.exception.BusinessException;
import com.example.data.entity.datasource.UseWriteDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@UseWriteDataSource
public class SellerDashboardFunnelQueryService {

    private static final String API_VERSION = "v1";
    private static final long STALE_THRESHOLD_MINUTES = 30L;

    private static final String DOMAIN_NORMAL = "NORMAL";
    private static final String DOMAIN_FUNDING = "FUNDING";
    private static final String DOMAIN_HOT_DEAL = "HOT_DEAL";

    private static final String EVENT_SEARCH_EXECUTED = "SEARCH_EXECUTED";
    private static final String EVENT_SEARCH_ITEM_CLICKED = "SEARCH_ITEM_CLICKED";
    private static final String EVENT_ORDER_CREATED = "ORDER_CREATED_EVENT";
    private static final String EVENT_ORDER_PAID = "ORDER_PAID_EVENT";
    private static final String EVENT_ORDER_CANCELLED = "ORDER_CANCELLED_EVENT";
    private static final String EVENT_ORDER_REFUNDED = "ORDER_REFUNDED_EVENT";
    private static final String EVENT_FUNDING_CREATED = "FUNDING_CREATED";
    private static final String EVENT_FUNDING_PARTICIPATED = "FUNDING_PARTICIPATED";
    private static final String EVENT_FUNDING_SUCCEEDED = "FUNDING_SUCCEEDED";
    private static final String EVENT_FUNDING_FAILED = "FUNDING_FAILED";
    private static final String EVENT_HOT_DEAL_STARTED = "HOT_DEAL_STARTED";
    private static final String EVENT_HOT_DEAL_PURCHASED = "HOT_DEAL_PURCHASED";
    private static final String EVENT_HOT_DEAL_ENDED = "HOT_DEAL_ENDED";

    private final AnalyticsJourneyEventRepository analyticsJourneyEventRepository;

    public SellerDashboardFunnelResponse getFunnel(Long sellerId, SellerDashboardOverviewQuery query) {
        validateSellerId(sellerId);

        QueryRangeContext queryRangeContext = resolveRange(query);
        List<AnalyticsJourneyEventCountRow> countRows = analyticsJourneyEventRepository.aggregateCountsBySellerIdAndOccurredAtBetween(
                sellerId,
                queryRangeContext.fromDateTime(),
                queryRangeContext.toDateTime()
        );
        LocalDateTime latestInRange = analyticsJourneyEventRepository.findLatestTimestampBySellerIdAndOccurredAtBetween(
                sellerId,
                queryRangeContext.fromDateTime(),
                queryRangeContext.toDateTime()
        );
        LocalDateTime latestGlobal = analyticsJourneyEventRepository.findLatestTimestamp();
        return buildResponse(query, queryRangeContext, countRows, latestInRange, latestGlobal);
    }

    public SellerDashboardFunnelResponse getFunnelByStore(Long storeId,
                                                          Long sellerId,
                                                          SellerDashboardOverviewQuery query) {
        validateStoreId(storeId);

        QueryRangeContext queryRangeContext = resolveRange(query);
        List<AnalyticsJourneyEventCountRow> countRows = hasPositiveId(sellerId)
                ? analyticsJourneyEventRepository.aggregateCountsByStoreIdAndSellerIdAndOccurredAtBetween(
                        storeId,
                        sellerId,
                        queryRangeContext.fromDateTime(),
                        queryRangeContext.toDateTime()
                )
                : analyticsJourneyEventRepository.aggregateCountsByStoreIdAndOccurredAtBetween(
                        storeId,
                        queryRangeContext.fromDateTime(),
                        queryRangeContext.toDateTime()
                );
        LocalDateTime latestInRange = hasPositiveId(sellerId)
                ? analyticsJourneyEventRepository.findLatestTimestampByStoreIdAndSellerIdAndOccurredAtBetween(
                        storeId,
                        sellerId,
                        queryRangeContext.fromDateTime(),
                        queryRangeContext.toDateTime()
                )
                : analyticsJourneyEventRepository.findLatestTimestampByStoreIdAndOccurredAtBetween(
                        storeId,
                        queryRangeContext.fromDateTime(),
                        queryRangeContext.toDateTime()
                );
        LocalDateTime latestGlobal = analyticsJourneyEventRepository.findLatestTimestamp();
        return buildResponse(query, queryRangeContext, countRows, latestInRange, latestGlobal);
    }

    private SellerDashboardFunnelResponse buildResponse(SellerDashboardOverviewQuery query,
                                                        QueryRangeContext queryRangeContext,
                                                        List<AnalyticsJourneyEventCountRow> countRows,
                                                        LocalDateTime latestInRange,
                                                        LocalDateTime latestGlobal) {
        DashboardLagStatus lagStatus = resolveLagStatus(latestGlobal);
        return SellerDashboardFunnelResponse.builder()
                .mode(query.mode())
                .queryRange(toQueryRange(queryRangeContext, query.timezone().getId()))
                .asOf(resolveAsOf(latestInRange, latestGlobal))
                .lagStatus(lagStatus)
                .partial(lagStatus == DashboardLagStatus.DEGRADED)
                .sales(toSalesFunnel(countRows))
                .funding(toFundingFunnel(countRows))
                .hotDeal(toHotDealFunnel(countRows))
                .apiVersion(API_VERSION)
                .build();
    }

    private SellerDashboardFunnelDomainResponse toSalesFunnel(List<AnalyticsJourneyEventCountRow> countRows) {
        long searchExecuted = count(countRows, EVENT_SEARCH_EXECUTED, null);
        long itemClicked = count(countRows, EVENT_SEARCH_ITEM_CLICKED, null);
        long orderCreated = count(countRows, EVENT_ORDER_CREATED, DOMAIN_NORMAL);
        long orderPaid = count(countRows, EVENT_ORDER_PAID, DOMAIN_NORMAL);
        long orderCancelled = count(countRows, EVENT_ORDER_CANCELLED, DOMAIN_NORMAL);
        long orderRefunded = count(countRows, EVENT_ORDER_REFUNDED, DOMAIN_NORMAL);

        return SellerDashboardFunnelDomainResponse.builder()
                .domainType(DOMAIN_NORMAL)
                .entryCount(searchExecuted)
                .conversionCount(orderPaid)
                .conversionRate(rate(searchExecuted, orderPaid))
                .steps(List.of(
                        step(EVENT_SEARCH_EXECUTED, searchExecuted),
                        step(EVENT_SEARCH_ITEM_CLICKED, itemClicked),
                        step(EVENT_ORDER_CREATED, orderCreated),
                        step(EVENT_ORDER_PAID, orderPaid),
                        step(EVENT_ORDER_CANCELLED, orderCancelled),
                        step(EVENT_ORDER_REFUNDED, orderRefunded)
                ))
                .build();
    }

    private SellerDashboardFunnelDomainResponse toFundingFunnel(List<AnalyticsJourneyEventCountRow> countRows) {
        long created = count(countRows, EVENT_FUNDING_CREATED, DOMAIN_FUNDING);
        long participated = count(countRows, EVENT_FUNDING_PARTICIPATED, DOMAIN_FUNDING);
        long succeeded = count(countRows, EVENT_FUNDING_SUCCEEDED, DOMAIN_FUNDING);
        long failed = count(countRows, EVENT_FUNDING_FAILED, DOMAIN_FUNDING);

        return SellerDashboardFunnelDomainResponse.builder()
                .domainType(DOMAIN_FUNDING)
                .entryCount(created)
                .conversionCount(succeeded)
                .conversionRate(rate(created, succeeded))
                .steps(List.of(
                        step(EVENT_FUNDING_CREATED, created),
                        step(EVENT_FUNDING_PARTICIPATED, participated),
                        step(EVENT_FUNDING_SUCCEEDED, succeeded),
                        step(EVENT_FUNDING_FAILED, failed)
                ))
                .build();
    }

    private SellerDashboardFunnelDomainResponse toHotDealFunnel(List<AnalyticsJourneyEventCountRow> countRows) {
        long started = count(countRows, EVENT_HOT_DEAL_STARTED, DOMAIN_HOT_DEAL);
        long purchased = count(countRows, EVENT_HOT_DEAL_PURCHASED, DOMAIN_HOT_DEAL);
        long ended = count(countRows, EVENT_HOT_DEAL_ENDED, DOMAIN_HOT_DEAL);

        return SellerDashboardFunnelDomainResponse.builder()
                .domainType(DOMAIN_HOT_DEAL)
                .entryCount(started)
                .conversionCount(purchased)
                .conversionRate(rate(started, purchased))
                .steps(List.of(
                        step(EVENT_HOT_DEAL_STARTED, started),
                        step(EVENT_HOT_DEAL_PURCHASED, purchased),
                        step(EVENT_HOT_DEAL_ENDED, ended)
                ))
                .build();
    }

    private long count(List<AnalyticsJourneyEventCountRow> countRows, String eventType, String domainType) {
        return countRows.stream()
                .filter(row -> eventType.equals(normalize(row.eventType())))
                .filter(row -> domainType == null || domainType.equals(normalize(row.domainType())))
                .mapToLong(row -> row.eventCount() == null ? 0L : row.eventCount())
                .sum();
    }

    private SellerDashboardFunnelStepResponse step(String step, long count) {
        return SellerDashboardFunnelStepResponse.builder()
                .step(step)
                .count(count)
                .build();
    }

    private double rate(long base, long converted) {
        if (base <= 0L) {
            return 0.0d;
        }
        return (double) converted / (double) base;
    }

    private Instant resolveAsOf(LocalDateTime latestInRange, LocalDateTime latestGlobal) {
        LocalDateTime reference = latestInRange != null ? latestInRange : latestGlobal;
        if (reference == null) {
            return Instant.now();
        }
        return reference.atZone(queryZone()).toInstant();
    }

    private DashboardLagStatus resolveLagStatus(LocalDateTime latestGlobal) {
        if (latestGlobal == null) {
            return DashboardLagStatus.HEALTHY;
        }
        LocalDateTime staleThreshold = LocalDateTime.now(queryZone()).minusMinutes(STALE_THRESHOLD_MINUTES);
        return latestGlobal.isBefore(staleThreshold)
                ? DashboardLagStatus.DEGRADED
                : DashboardLagStatus.HEALTHY;
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

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private java.time.ZoneId queryZone() {
        return java.time.ZoneId.of("Asia/Seoul");
    }

    private record QueryRangeContext(
            LocalDate fromDate,
            LocalDate toDate,
            LocalDateTime fromDateTime,
            LocalDateTime toDateTime,
            DashboardSeriesBucket bucket
    ) {
    }
}
