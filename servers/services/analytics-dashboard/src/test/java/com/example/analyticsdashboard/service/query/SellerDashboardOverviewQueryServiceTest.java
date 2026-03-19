package com.example.analyticsdashboard.service.query;

import com.example.analyticsdashboard.dto.query.SellerDashboardOverviewQuery;
import com.example.analyticsdashboard.dto.response.SellerDashboardFunnelResponse;
import com.example.analyticsdashboard.dto.response.SellerDashboardOverviewResponse;
import com.example.analyticsdashboard.entity.AnalyticsRawSalesEvent;
import com.example.analyticsdashboard.entity.AnalyticsRawSearchEvent;
import com.example.analyticsdashboard.exception.AnalyticsDashboardErrorCode;
import com.example.analyticsdashboard.repository.AnalyticsDimItemSnapshotRepository;
import com.example.analyticsdashboard.repository.AnalyticsRawSalesEventRepository;
import com.example.analyticsdashboard.repository.AnalyticsRawSearchEventRepository;
import com.example.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private AnalyticsRawSearchEventRepository rawSearchEventRepository;

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
    void getOverview_calculatesSalesAndSearchKpiFromRawEvents() {
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

        AnalyticsRawSearchEvent searchExecuted = AnalyticsRawSearchEvent.builder()
                .eventId("search-1")
                .eventType("SEARCH_EXECUTED")
                .storeId(10L)
                .sellerId(20L)
                .occurredAt(now.minusMinutes(40))
                .ingestedAt(now.minusMinutes(40))
                .build();
        AnalyticsRawSearchEvent clicked = AnalyticsRawSearchEvent.builder()
                .eventId("search-2")
                .eventType("SEARCH_ITEM_CLICKED")
                .storeId(10L)
                .sellerId(20L)
                .occurredAt(now.minusMinutes(30))
                .ingestedAt(now.minusMinutes(30))
                .build();
        when(rawSearchEventRepository.findByStoreIdAndSellerIdAndOccurredAtBetween(anyLong(), anyLong(), any(), any()))
                .thenReturn(List.of(searchExecuted, clicked));
        when(dimItemSnapshotRepository.findByStoreIdAndSellerId(anyLong(), anyLong())).thenReturn(List.of());

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
        verify(rawSearchEventRepository).findBySellerIdAndOccurredAtBetween(anyLong(), any(), any());
        verify(dimItemSnapshotRepository).findBySellerId(anyLong());
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
        verify(rawSearchEventRepository).findByStoreIdAndOccurredAtBetween(anyLong(), any(), any());
        verify(dimItemSnapshotRepository).findByStoreId(anyLong());
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
                .thenReturn(SellerDashboardFunnelResponse.builder().apiVersion("v1").build());
        lenient().when(sellerDashboardFunnelQueryService.getFunnelByStore(anyLong(), any(), any()))
                .thenReturn(SellerDashboardFunnelResponse.builder().apiVersion("v1").build());
        lenient().when(rawSalesEventRepository.findByStoreIdAndOccurredAtBetween(anyLong(), any(), any()))
                .thenReturn(List.of());
        lenient().when(rawSalesEventRepository.findBySellerIdAndOccurredAtBetween(anyLong(), any(), any()))
                .thenReturn(List.of());
        lenient().when(rawSalesEventRepository.findByStoreIdAndSellerIdAndOccurredAtBetween(anyLong(), anyLong(), any(), any()))
                .thenReturn(List.of());
        lenient().when(rawSearchEventRepository.findByStoreIdAndOccurredAtBetween(anyLong(), any(), any()))
                .thenReturn(List.of());
        lenient().when(rawSearchEventRepository.findBySellerIdAndOccurredAtBetween(anyLong(), any(), any()))
                .thenReturn(List.of());
        lenient().when(rawSearchEventRepository.findByStoreIdAndSellerIdAndOccurredAtBetween(anyLong(), anyLong(), any(), any()))
                .thenReturn(List.of());
        lenient().when(dimItemSnapshotRepository.findByStoreId(anyLong())).thenReturn(List.of());
        lenient().when(dimItemSnapshotRepository.findBySellerId(anyLong())).thenReturn(List.of());
        lenient().when(dimItemSnapshotRepository.findByStoreIdAndSellerId(anyLong(), anyLong())).thenReturn(List.of());
    }
}
