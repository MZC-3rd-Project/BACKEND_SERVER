package com.example.analyticsdashboard.service.query;

import com.example.analyticsdashboard.dto.query.SellerDashboardOverviewQuery;
import com.example.analyticsdashboard.dto.response.SellerDashboardFunnelResponse;
import com.example.analyticsdashboard.entity.AnalyticsJourneyEvent;
import com.example.analyticsdashboard.exception.AnalyticsDashboardErrorCode;
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
        when(analyticsJourneyEventRepository.findByStoreIdAndSellerIdAndOccurredAtBetween(anyLong(), anyLong(), any(), any()))
                .thenReturn(List.of(
                        journey("evt-1", "SEARCH_EXECUTED", "SEARCH", now.minusHours(5)),
                        journey("evt-2", "SEARCH_ITEM_CLICKED", "SEARCH", now.minusHours(4)),
                        journey("evt-3", "ORDER_CREATED_EVENT", "NORMAL", now.minusHours(3)),
                        journey("evt-4", "ORDER_PAID_EVENT", "NORMAL", now.minusHours(2)),
                        journey("evt-5", "FUNDING_CREATED", "FUNDING", now.minusHours(3)),
                        journey("evt-6", "FUNDING_SUCCEEDED", "FUNDING", now.minusHours(1)),
                        journey("evt-7", "HOT_DEAL_STARTED", "HOT_DEAL", now.minusHours(2)),
                        journey("evt-8", "HOT_DEAL_PURCHASED", "HOT_DEAL", now.minusMinutes(30))
                ));

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
    }

    @Test
    void getFunnel_bySeller_usesSellerScopedRepository() {
        when(analyticsJourneyEventRepository.findBySellerIdAndOccurredAtBetween(anyLong(), any(), any()))
                .thenReturn(List.of());

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

        verify(analyticsJourneyEventRepository).findBySellerIdAndOccurredAtBetween(anyLong(), any(), any());
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

    private AnalyticsJourneyEvent journey(String eventId, String eventType, String domainType, LocalDateTime occurredAt) {
        return AnalyticsJourneyEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .domainType(domainType)
                .occurredAt(occurredAt)
                .ingestedAt(occurredAt)
                .build();
    }
}
