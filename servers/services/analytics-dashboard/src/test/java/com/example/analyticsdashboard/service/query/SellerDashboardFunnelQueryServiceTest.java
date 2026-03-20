package com.example.analyticsdashboard.service.query;

import com.example.analyticsdashboard.dto.query.SellerDashboardOverviewQuery;
import com.example.analyticsdashboard.dto.response.DashboardLagStatus;
import com.example.analyticsdashboard.dto.response.SellerDashboardFunnelResponse;
import com.example.analyticsdashboard.exception.AnalyticsDashboardErrorCode;
import com.example.analyticsdashboard.repository.AnalyticsJourneyEventCountRow;
import com.example.analyticsdashboard.repository.AnalyticsJourneyEventRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerDashboardFunnelQueryServiceTest {

    @Mock
    private AnalyticsJourneyEventRepository analyticsJourneyEventRepository;

    @InjectMocks
    private SellerDashboardFunnelQueryService service;

    @Test
    void getFunnelByStore_aggregatesSalesFundingAndHotDealSteps() {
        LocalDateTime now = LocalDateTime.now();
        when(analyticsJourneyEventRepository.aggregateCountsByStoreIdAndSellerIdAndOccurredAtBetween(anyLong(), anyLong(), any(), any()))
                .thenReturn(List.of(
                        countRow("SEARCH_EXECUTED", "SEARCH", 1L),
                        countRow("SEARCH_ITEM_CLICKED", "SEARCH", 1L),
                        countRow("ORDER_CREATED_EVENT", "NORMAL", 1L),
                        countRow("ORDER_PAID_EVENT", "NORMAL", 1L),
                        countRow("FUNDING_CREATED", "FUNDING", 1L),
                        countRow("FUNDING_SUCCEEDED", "FUNDING", 1L),
                        countRow("HOT_DEAL_STARTED", "HOT_DEAL", 1L),
                        countRow("HOT_DEAL_PURCHASED", "HOT_DEAL", 1L)
                ));
        when(analyticsJourneyEventRepository.findLatestTimestampByStoreIdAndSellerIdAndOccurredAtBetween(anyLong(), anyLong(), any(), any()))
                .thenReturn(now.minusMinutes(5));
        when(analyticsJourneyEventRepository.findLatestTimestamp()).thenReturn(now.minusMinutes(2));

        SellerDashboardOverviewQuery query = SellerDashboardOverviewQuery.of(
                "DAILY",
                "2026-03-19",
                null,
                null,
                null,
                null,
                "Asia/Seoul"
        );

        SellerDashboardFunnelResponse response = service.getFunnelByStore(10L, 20L, query);

        assertThat(response.getSales().getEntryCount()).isEqualTo(1L);
        assertThat(response.getSales().getConversionCount()).isEqualTo(1L);
        assertThat(response.getSales().getConversionRate()).isEqualTo(1.0d);
        assertThat(response.getFunding().getEntryCount()).isEqualTo(1L);
        assertThat(response.getFunding().getConversionCount()).isEqualTo(1L);
        assertThat(response.getHotDeal().getEntryCount()).isEqualTo(1L);
        assertThat(response.getHotDeal().getConversionCount()).isEqualTo(1L);
        assertThat(response.getLagStatus()).isEqualTo(DashboardLagStatus.HEALTHY);
        assertThat(response.isPartial()).isFalse();
    }

    @Test
    void getFunnel_bySeller_usesSellerScopedRepository() {
        when(analyticsJourneyEventRepository.aggregateCountsBySellerIdAndOccurredAtBetween(anyLong(), any(), any()))
                .thenReturn(List.of());
        lenient().when(analyticsJourneyEventRepository.findLatestTimestampBySellerIdAndOccurredAtBetween(anyLong(), any(), any()))
                .thenReturn(null);
        lenient().when(analyticsJourneyEventRepository.findLatestTimestamp()).thenReturn(null);

        SellerDashboardOverviewQuery query = SellerDashboardOverviewQuery.of(
                "DAILY",
                "2026-03-19",
                null,
                null,
                null,
                null,
                null
        );

        service.getFunnel(100L, query);

        verify(analyticsJourneyEventRepository).aggregateCountsBySellerIdAndOccurredAtBetween(anyLong(), any(), any());
    }

    @Test
    void getFunnel_marksResponseDegradedWhenGlobalFreshnessIsStale() {
        LocalDateTime now = LocalDateTime.now();
        when(analyticsJourneyEventRepository.aggregateCountsBySellerIdAndOccurredAtBetween(anyLong(), any(), any()))
                .thenReturn(List.of(countRow("SEARCH_EXECUTED", "SEARCH", 1L)));
        when(analyticsJourneyEventRepository.findLatestTimestampBySellerIdAndOccurredAtBetween(anyLong(), any(), any()))
                .thenReturn(now.minusHours(1));
        when(analyticsJourneyEventRepository.findLatestTimestamp())
                .thenReturn(now.minusHours(1));

        SellerDashboardOverviewQuery query = SellerDashboardOverviewQuery.of(
                "DAILY",
                "2026-03-19",
                null,
                null,
                null,
                null,
                null
        );

        SellerDashboardFunnelResponse response = service.getFunnel(100L, query);

        assertThat(response.getLagStatus()).isEqualTo(DashboardLagStatus.DEGRADED);
        assertThat(response.isPartial()).isTrue();
    }

    @Test
    void getFunnel_throwsWhenSellerIdInvalid() {
        SellerDashboardOverviewQuery query = SellerDashboardOverviewQuery.of(
                "DAILY",
                "2026-03-19",
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.getFunnel(0L, query))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AnalyticsDashboardErrorCode.INVALID_DASHBOARD_QUERY_PARAMETER);
                });
    }

    private AnalyticsJourneyEventCountRow countRow(String eventType, String domainType, Long count) {
        return new AnalyticsJourneyEventCountRow(eventType, domainType, count);
    }
}
