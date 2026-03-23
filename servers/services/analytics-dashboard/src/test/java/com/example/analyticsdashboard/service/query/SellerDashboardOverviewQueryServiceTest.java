package com.example.analyticsdashboard.service.query;

import com.example.analyticsdashboard.dto.query.SellerDashboardOverviewQuery;
import com.example.analyticsdashboard.dto.response.DashboardLagStatus;
import com.example.analyticsdashboard.dto.response.SellerDashboardFunnelDomainResponse;
import com.example.analyticsdashboard.dto.response.SellerDashboardFunnelResponse;
import com.example.analyticsdashboard.dto.response.SellerDashboardFunnelStepResponse;
import com.example.analyticsdashboard.dto.response.SellerDashboardOverviewResponse;
import com.example.analyticsdashboard.dto.response.SellerDashboardReviewKpiResponse;
import com.example.analyticsdashboard.entity.AnalyticsDimItemSnapshot;
import com.example.analyticsdashboard.repository.AnalyticsItemStatusCountRow;
import com.example.analyticsdashboard.repository.AnalyticsRawSalesEventAggregateRow;
import com.example.analyticsdashboard.entity.AnalyticsRawSalesEvent;
import com.example.analyticsdashboard.exception.AnalyticsDashboardErrorCode;
import com.example.analyticsdashboard.repository.AnalyticsDimItemSnapshotRepository;
import com.example.analyticsdashboard.repository.AnalyticsRawSalesEventRepository;
import com.example.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerDashboardOverviewQueryServiceTest {

    @Mock
    private AnalyticsRawSalesEventRepository rawSalesEventRepository;

    @Mock
    private AnalyticsDimItemSnapshotRepository dimItemSnapshotRepository;

    @Mock
    private SellerDashboardFunnelQueryService sellerDashboardFunnelQueryService;

    @InjectMocks
    private SellerDashboardOverviewQueryService service;

    @Test
    void getOverview_daily_returnsSingleSeriesAndDayRange() {
        stubEmptyRepos();
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
        assertThat(response.getExtensions()).containsKey("funnel");
        assertThat(response.getLagStatus()).isEqualTo(DashboardLagStatus.HEALTHY);
    }

    @Test
    void getOverview_rangeDay_returnsSeriesForEachDate() {
        stubEmptyRepos();
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
    void getOverview_calculatesSalesAndSearchKpiFromSalesAggregatesAndFunnel() {
        LocalDateTime now = LocalDateTime.now();
        AnalyticsRawSalesEvent created = AnalyticsRawSalesEvent.builder()
                .eventId("sales-1")
                .eventType("PURCHASE_CREATED")
                .storeId(10L)
                .sellerId(20L)
                .itemId(30L)
                .grossAmount(10000L)
                .netAmount(10000L)
                .occurredAt(now.minusHours(2))
                .ingestedAt(now.minusHours(2))
                .build();
        AnalyticsRawSalesEvent cancelled = AnalyticsRawSalesEvent.builder()
                .eventId("sales-2")
                .eventType("PURCHASE_CANCELLED")
                .storeId(10L)
                .sellerId(20L)
                .itemId(30L)
                .grossAmount(0L)
                .netAmount(-2000L)
                .occurredAt(now.minusHours(1))
                .ingestedAt(now.minusHours(1))
                .build();
        when(rawSalesEventRepository.findByStoreIdAndSellerIdAndOccurredAtBetween(anyLong(), anyLong(), any(), any()))
                .thenReturn(List.of(created, cancelled));
        when(rawSalesEventRepository.aggregateByStoreIdAndSellerIdAndOccurredAtBetween(anyLong(), anyLong(), any(), any()))
                .thenReturn(List.of(
                        new AnalyticsRawSalesEventAggregateRow("PURCHASE_CREATED", 1L, 10000L, 10000L),
                        new AnalyticsRawSalesEventAggregateRow("PURCHASE_CANCELLED", 1L, 0L, -2000L)
                ));
        when(rawSalesEventRepository.findLatestTimestampByStoreIdAndSellerIdAndOccurredAtBetween(anyLong(), anyLong(), any(), any()))
                .thenReturn(now.minusMinutes(20));
        when(dimItemSnapshotRepository.countByStatusForStoreIdAndSellerId(anyLong(), anyLong()))
                .thenReturn(List.of(new AnalyticsItemStatusCountRow("ON_SALE", 2L)));
        when(sellerDashboardFunnelQueryService.getFunnelByStore(anyLong(), any(), any()))
                .thenReturn(funnel(1L, 1L, DashboardLagStatus.HEALTHY, false));

        SellerDashboardOverviewQuery query = SellerDashboardOverviewQuery.of(
                "DAILY",
                "2026-02-26",
                null,
                null,
                null,
                null,
                "Asia/Seoul"
        );

        SellerDashboardOverviewResponse response = service.getOverviewByStore(10L, 20L, query);

        assertThat(response.getSales().getGrossSales()).isEqualTo(10000L);
        assertThat(response.getSales().getNetSales()).isEqualTo(8000L);
        assertThat(response.getSales().getOrderCount()).isEqualTo(1L);
        assertThat(response.getSales().getCancelCount()).isEqualTo(1L);
        assertThat(response.getSearch().getSearchCount()).isEqualTo(1L);
        assertThat(response.getSearch().getClickCount()).isEqualTo(1L);
        assertThat(response.getSearch().getCtr()).isEqualTo(1.0d);
    }

    @Test
    void getOverview_includesReviewMetricsInExtensions() {
        stubEmptyRepos();
        when(dimItemSnapshotRepository.findBySellerId(20L)).thenReturn(List.of(
                AnalyticsDimItemSnapshot.builder()
                        .itemId(1L)
                        .storeId(10L)
                        .sellerId(20L)
                        .itemType("GOODS")
                        .itemStatus("ON_SALE")
                        .price(1000L)
                        .stockQuantity(10L)
                        .reviewCount(2L)
                        .averageRating(new BigDecimal("4.50"))
                        .snapshotAt(LocalDateTime.now())
                        .build(),
                AnalyticsDimItemSnapshot.builder()
                        .itemId(2L)
                        .storeId(10L)
                        .sellerId(20L)
                        .itemType("GOODS")
                        .itemStatus("ON_SALE")
                        .price(2000L)
                        .stockQuantity(5L)
                        .reviewCount(1L)
                        .averageRating(new BigDecimal("3.00"))
                        .snapshotAt(LocalDateTime.now())
                        .build()
        ));

        SellerDashboardOverviewQuery query = SellerDashboardOverviewQuery.of(
                "DAILY",
                "2026-02-26",
                null,
                null,
                null,
                null,
                "Asia/Seoul"
        );

        SellerDashboardOverviewResponse response = service.getOverview(20L, query);

        assertThat(response.getExtensions()).containsKey("review");
        SellerDashboardReviewKpiResponse review =
                (SellerDashboardReviewKpiResponse) response.getExtensions().get("review");
        assertThat(review).isNotNull();
        assertThat(review.getReviewCount()).isEqualTo(3L);
        assertThat(review.getReviewedItemCount()).isEqualTo(2L);
        assertThat(review.getAverageRating()).isEqualByComparingTo("4.00");
    }

    @Test
    void getOverview_reusesFunnelFreshnessStatus() {
        stubEmptyRepos();
        when(sellerDashboardFunnelQueryService.getFunnel(anyLong(), any()))
                .thenReturn(SellerDashboardFunnelResponse.builder()
                        .apiVersion("v1")
                        .lagStatus(DashboardLagStatus.DEGRADED)
                        .partial(true)
                        .build());

        SellerDashboardOverviewQuery query = SellerDashboardOverviewQuery.of(
                "DAILY",
                "2026-02-26",
                null,
                null,
                null,
                null,
                null
        );

        SellerDashboardOverviewResponse response = service.getOverview(100L, query);

        assertThat(response.getLagStatus()).isEqualTo(DashboardLagStatus.DEGRADED);
        assertThat(response.isPartial()).isTrue();
    }

    @Test
    void getOverview_bySeller_usesSellerRepositories() {
        stubEmptyRepos();
        SellerDashboardOverviewQuery query = SellerDashboardOverviewQuery.of(
                "DAILY",
                "2026-02-26",
                null,
                null,
                null,
                null,
                null
        );

        service.getOverview(100L, query);

        verify(rawSalesEventRepository).findBySellerIdAndOccurredAtBetween(anyLong(), any(), any());
        verify(dimItemSnapshotRepository).countByStatusForSellerId(anyLong());
        verify(rawSalesEventRepository, never()).findByStoreIdAndOccurredAtBetween(anyLong(), any(), any());
    }

    @Test
    void getOverviewByStore_withoutSellerFilter_usesStoreRepositories() {
        stubEmptyRepos();
        SellerDashboardOverviewQuery query = SellerDashboardOverviewQuery.of(
                "DAILY",
                "2026-02-26",
                null,
                null,
                null,
                null,
                null
        );

        service.getOverviewByStore(10L, null, query);

        verify(rawSalesEventRepository).findByStoreIdAndOccurredAtBetween(anyLong(), any(), any());
        verify(dimItemSnapshotRepository).countByStatusForStoreId(anyLong());
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

    private void stubEmptyRepos() {
        lenient().when(sellerDashboardFunnelQueryService.getFunnel(anyLong(), any()))
                .thenReturn(funnel(0L, 0L, DashboardLagStatus.HEALTHY, false));
        lenient().when(sellerDashboardFunnelQueryService.getFunnelByStore(anyLong(), any(), any()))
                .thenReturn(funnel(0L, 0L, DashboardLagStatus.HEALTHY, false));
        lenient().when(rawSalesEventRepository.findByStoreIdAndOccurredAtBetween(anyLong(), any(), any()))
                .thenReturn(List.of());
        lenient().when(rawSalesEventRepository.findBySellerIdAndOccurredAtBetween(anyLong(), any(), any()))
                .thenReturn(List.of());
        lenient().when(rawSalesEventRepository.findByStoreIdAndSellerIdAndOccurredAtBetween(anyLong(), anyLong(), any(), any()))
                .thenReturn(List.of());
        lenient().when(rawSalesEventRepository.aggregateByStoreIdAndOccurredAtBetween(anyLong(), any(), any()))
                .thenReturn(List.of());
        lenient().when(rawSalesEventRepository.aggregateBySellerIdAndOccurredAtBetween(anyLong(), any(), any()))
                .thenReturn(List.of());
        lenient().when(rawSalesEventRepository.aggregateByStoreIdAndSellerIdAndOccurredAtBetween(anyLong(), anyLong(), any(), any()))
                .thenReturn(List.of());
        lenient().when(rawSalesEventRepository.findLatestTimestampByStoreIdAndOccurredAtBetween(anyLong(), any(), any()))
                .thenReturn(null);
        lenient().when(rawSalesEventRepository.findLatestTimestampBySellerIdAndOccurredAtBetween(anyLong(), any(), any()))
                .thenReturn(null);
        lenient().when(rawSalesEventRepository.findLatestTimestampByStoreIdAndSellerIdAndOccurredAtBetween(anyLong(), anyLong(), any(), any()))
                .thenReturn(null);
        lenient().when(dimItemSnapshotRepository.countByStatusForStoreId(anyLong())).thenReturn(List.of());
        lenient().when(dimItemSnapshotRepository.countByStatusForSellerId(anyLong())).thenReturn(List.of());
        lenient().when(dimItemSnapshotRepository.countByStatusForStoreIdAndSellerId(anyLong(), anyLong())).thenReturn(List.of());
        lenient().when(dimItemSnapshotRepository.findByStoreId(anyLong())).thenReturn(List.of());
        lenient().when(dimItemSnapshotRepository.findBySellerId(anyLong())).thenReturn(List.of());
        lenient().when(dimItemSnapshotRepository.findByStoreIdAndSellerId(anyLong(), anyLong())).thenReturn(List.of());
    }

    private SellerDashboardFunnelResponse funnel(long searchCount,
                                                 long clickCount,
                                                 DashboardLagStatus lagStatus,
                                                 boolean partial) {
        return SellerDashboardFunnelResponse.builder()
                .apiVersion("v1")
                .lagStatus(lagStatus)
                .partial(partial)
                .sales(SellerDashboardFunnelDomainResponse.builder()
                        .domainType("NORMAL")
                        .steps(List.of(
                                SellerDashboardFunnelStepResponse.builder()
                                        .step("SEARCH_EXECUTED")
                                        .count(searchCount)
                                        .build(),
                                SellerDashboardFunnelStepResponse.builder()
                                        .step("SEARCH_ITEM_CLICKED")
                                        .count(clickCount)
                                        .build()
                        ))
                        .build())
                .build();
    }
}
