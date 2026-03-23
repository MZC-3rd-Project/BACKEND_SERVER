package com.example.analyticsdashboard.service.ingest;

import com.example.analyticsdashboard.consumer.item.AnalyticsItemEventMessage;
import com.example.analyticsdashboard.consumer.order.AnalyticsOrderEventMessage;
import com.example.analyticsdashboard.consumer.sales.AnalyticsSalesEventMessage;
import com.example.analyticsdashboard.consumer.search.AnalyticsSearchEventMessage;
import com.example.analyticsdashboard.entity.AnalyticsDimItemSnapshot;
import com.example.analyticsdashboard.entity.AnalyticsJourneyEvent;
import com.example.analyticsdashboard.entity.AnalyticsRawSalesEvent;
import com.example.analyticsdashboard.entity.AnalyticsRawSearchEvent;
import com.example.analyticsdashboard.repository.AnalyticsDimItemSnapshotRepository;
import com.example.analyticsdashboard.repository.AnalyticsJourneyEventRepository;
import com.example.analyticsdashboard.repository.AnalyticsRawSalesEventRepository;
import com.example.analyticsdashboard.repository.AnalyticsRawSearchEventRepository;
import com.example.core.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsEventIngestServiceTest {

    @Mock
    private AnalyticsDimItemSnapshotRepository dimItemSnapshotRepository;

    @Mock
    private AnalyticsRawSalesEventRepository rawSalesEventRepository;

    @Mock
    private AnalyticsRawSearchEventRepository rawSearchEventRepository;

    @Mock
    private AnalyticsJourneyEventRepository analyticsJourneyEventRepository;

    @InjectMocks
    private AnalyticsEventIngestService service;

    @Test
    void ingestItemEvent_itemCreated_savesSnapshot() {
        when(dimItemSnapshotRepository.findById(101L)).thenReturn(Optional.empty());

        AnalyticsItemEventMessage event = JsonUtils.fromJson("""
                {
                  "eventId": "evt-item-created",
                  "eventType": "ITEM_CREATED",
                  "itemId": 101,
                  "storeId": 201,
                  "sellerId": 301,
                  "itemType": "GOODS",
                  "status": "ON_SALE",
                  "price": 15000
                }
                """, AnalyticsItemEventMessage.class);

        service.ingestItemEvent(event);

        ArgumentCaptor<AnalyticsDimItemSnapshot> captor = ArgumentCaptor.forClass(AnalyticsDimItemSnapshot.class);
        verify(dimItemSnapshotRepository).save(captor.capture());
        AnalyticsDimItemSnapshot saved = captor.getValue();
        assertThat(saved.getItemId()).isEqualTo(101L);
        assertThat(saved.getStoreId()).isEqualTo(201L);
        assertThat(saved.getSellerId()).isEqualTo(301L);
        assertThat(saved.getItemType()).isEqualTo("GOODS");
        assertThat(saved.getItemStatus()).isEqualTo("ON_SALE");
        assertThat(saved.getPrice()).isEqualTo(15000L);
    }

    @Test
    void ingestItemEvent_itemUpdated_updatesReviewMetricsOnSnapshot() {
        AnalyticsDimItemSnapshot snapshot = AnalyticsDimItemSnapshot.builder()
                .itemId(101L)
                .storeId(201L)
                .sellerId(301L)
                .itemType("GOODS")
                .itemStatus("ON_SALE")
                .price(15000L)
                .stockQuantity(10L)
                .reviewCount(0L)
                .averageRating(BigDecimal.ZERO.setScale(2))
                .snapshotAt(LocalDateTime.now())
                .build();
        when(dimItemSnapshotRepository.findById(101L)).thenReturn(Optional.of(snapshot));

        AnalyticsItemEventMessage event = JsonUtils.fromJson("""
                {
                  "eventId": "evt-item-updated",
                  "eventType": "ITEM_UPDATED",
                  "itemId": 101,
                  "price": 15000,
                  "reviewCount": 8,
                  "averageRating": 4.25
                }
                """, AnalyticsItemEventMessage.class);

        service.ingestItemEvent(event);

        ArgumentCaptor<AnalyticsDimItemSnapshot> captor = ArgumentCaptor.forClass(AnalyticsDimItemSnapshot.class);
        verify(dimItemSnapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getReviewCount()).isEqualTo(8L);
        assertThat(captor.getValue().getAverageRating()).isEqualByComparingTo("4.25");
    }

    @Test
    void ingestSalesEvent_cancel_resolvesOwnershipFromPreviousRaw() {
        when(rawSalesEventRepository.findByEventId("evt-cancel")).thenReturn(Optional.empty());
        AnalyticsRawSalesEvent previous = AnalyticsRawSalesEvent.builder()
                .eventId("prev-created")
                .eventType("PURCHASE_CREATED")
                .storeId(11L)
                .sellerId(22L)
                .itemId(33L)
                .purchaseId(99L)
                .grossAmount(10000L)
                .netAmount(10000L)
                .occurredAt(LocalDateTime.now().minusMinutes(1))
                .ingestedAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(rawSalesEventRepository.findTopByPurchaseIdOrderByOccurredAtDesc(99L))
                .thenReturn(Optional.of(previous));

        AnalyticsSalesEventMessage event = JsonUtils.fromJson("""
                {
                  "eventId": "evt-cancel",
                  "eventType": "PURCHASE_CANCELLED",
                  "purchaseId": 99
                }
                """, AnalyticsSalesEventMessage.class);

        service.ingestSalesEvent(event);

        ArgumentCaptor<AnalyticsRawSalesEvent> captor = ArgumentCaptor.forClass(AnalyticsRawSalesEvent.class);
        verify(rawSalesEventRepository).save(captor.capture());
        AnalyticsRawSalesEvent saved = captor.getValue();
        assertThat(saved.getStoreId()).isEqualTo(11L);
        assertThat(saved.getSellerId()).isEqualTo(22L);
        assertThat(saved.getItemId()).isEqualTo(33L);
        assertThat(saved.getNetAmount()).isEqualTo(-10000L);
        assertThat(saved.getJourneyId()).isEqualTo("purchase-99");

        ArgumentCaptor<AnalyticsJourneyEvent> journeyCaptor = ArgumentCaptor.forClass(AnalyticsJourneyEvent.class);
        verify(analyticsJourneyEventRepository).save(journeyCaptor.capture());
        assertThat(journeyCaptor.getValue().getPurchaseId()).isEqualTo(99L);
        assertThat(journeyCaptor.getValue().getEventSequence()).isEqualTo(0);
    }

    @Test
    void ingestSearchEvent_resolvesOwnershipByItemSnapshot() {
        when(rawSearchEventRepository.findByEventId("evt-search")).thenReturn(Optional.empty());
        AnalyticsDimItemSnapshot snapshot = AnalyticsDimItemSnapshot.builder()
                .itemId(333L)
                .storeId(444L)
                .sellerId(555L)
                .itemType("GOODS")
                .itemStatus("ON_SALE")
                .price(1000L)
                .stockQuantity(10L)
                .snapshotAt(LocalDateTime.now())
                .build();
        when(dimItemSnapshotRepository.findById(333L)).thenReturn(Optional.of(snapshot));
        when(rawSearchEventRepository.save(any(AnalyticsRawSearchEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AnalyticsSearchEventMessage event = JsonUtils.fromJson("""
                {
                  "eventId": "evt-search",
                  "eventType": "SEARCH_EXECUTED",
                  "itemId": 333,
                  "queryHash": "hash-value",
                  "sessionId": "sess-1"
                }
                """, AnalyticsSearchEventMessage.class);

        service.ingestSearchEvent(event);

        ArgumentCaptor<AnalyticsRawSearchEvent> captor = ArgumentCaptor.forClass(AnalyticsRawSearchEvent.class);
        verify(rawSearchEventRepository).save(captor.capture());
        AnalyticsRawSearchEvent saved = captor.getValue();
        assertThat(saved.getStoreId()).isEqualTo(444L);
        assertThat(saved.getSellerId()).isEqualTo(555L);
        assertThat(saved.getItemId()).isEqualTo(333L);
        assertThat(saved.getQueryHash()).isEqualTo("hash-value");

        ArgumentCaptor<AnalyticsJourneyEvent> journeyCaptor = ArgumentCaptor.forClass(AnalyticsJourneyEvent.class);
        verify(analyticsJourneyEventRepository).save(journeyCaptor.capture());
        AnalyticsJourneyEvent journeyEvent = journeyCaptor.getValue();
        assertThat(journeyEvent.getDomainType()).isEqualTo("SEARCH");
        assertThat(journeyEvent.getSessionId()).isEqualTo("sess-1");
        assertThat(journeyEvent.getEventSequence()).isEqualTo(0);
    }

    @Test
    void ingestSearchEvent_searchExecuted_expandsJourneyByResultOwnership() {
        when(rawSearchEventRepository.findByEventId("evt-search-results")).thenReturn(Optional.empty());
        when(dimItemSnapshotRepository.findById(101L)).thenReturn(Optional.of(
                AnalyticsDimItemSnapshot.builder()
                        .itemId(101L)
                        .storeId(201L)
                        .sellerId(301L)
                        .itemType("GOODS")
                        .itemStatus("ON_SALE")
                        .price(1000L)
                        .stockQuantity(10L)
                        .snapshotAt(LocalDateTime.now())
                        .build()
        ));
        when(dimItemSnapshotRepository.findById(102L)).thenReturn(Optional.of(
                AnalyticsDimItemSnapshot.builder()
                        .itemId(102L)
                        .storeId(201L)
                        .sellerId(301L)
                        .itemType("GOODS")
                        .itemStatus("ON_SALE")
                        .price(2000L)
                        .stockQuantity(5L)
                        .snapshotAt(LocalDateTime.now())
                        .build()
        ));
        when(dimItemSnapshotRepository.findById(103L)).thenReturn(Optional.of(
                AnalyticsDimItemSnapshot.builder()
                        .itemId(103L)
                        .storeId(202L)
                        .sellerId(302L)
                        .itemType("GOODS")
                        .itemStatus("ON_SALE")
                        .price(3000L)
                        .stockQuantity(3L)
                        .snapshotAt(LocalDateTime.now())
                        .build()
        ));

        AnalyticsSearchEventMessage event = JsonUtils.fromJson("""
                {
                  "eventId": "evt-search-results",
                  "eventType": "SEARCH_EXECUTED",
                  "queryHash": "hash-result",
                  "userId": 777,
                  "resultItemIds": [101, 102, 103]
                }
                """, AnalyticsSearchEventMessage.class);

        service.ingestSearchEvent(event);

        ArgumentCaptor<AnalyticsJourneyEvent> journeyCaptor = ArgumentCaptor.forClass(AnalyticsJourneyEvent.class);
        verify(analyticsJourneyEventRepository, times(2)).save(journeyCaptor.capture());

        List<AnalyticsJourneyEvent> saved = journeyCaptor.getAllValues();
        assertThat(saved)
                .extracting(AnalyticsJourneyEvent::getEventSequence)
                .containsExactly(0, 1);
        assertThat(saved)
                .extracting(AnalyticsJourneyEvent::getStoreId)
                .containsExactly(201L, 202L);
        assertThat(saved)
                .extracting(AnalyticsJourneyEvent::getSellerId)
                .containsExactly(301L, 302L);
        assertThat(saved)
                .extracting(AnalyticsJourneyEvent::getQueryHash)
                .containsOnly("hash-result");
    }

    @Test
    void ingestOrderEvent_paid_clonesCreatedJourneyContext() {
        AnalyticsJourneyEvent created = AnalyticsJourneyEvent.builder()
                .eventId("evt-order-created")
                .eventSequence(0)
                .eventType("ORDER_CREATED_EVENT")
                .domainType("NORMAL")
                .channelType("NORMAL")
                .userId(77L)
                .journeyId("order-9001")
                .sellerId(200L)
                .storeId(100L)
                .itemId(300L)
                .orderId(9001L)
                .quantity(2)
                .amount(12000L)
                .occurredAt(LocalDateTime.now().minusMinutes(3))
                .ingestedAt(LocalDateTime.now().minusMinutes(3))
                .build();
        when(analyticsJourneyEventRepository.findByOrderIdAndEventTypeOrderByOccurredAtAsc(9001L, "ORDER_CREATED_EVENT"))
                .thenReturn(List.of(created));

        AnalyticsOrderEventMessage event = JsonUtils.fromJson("""
                {
                  "eventId": "evt-order-paid",
                  "eventType": "ORDER_PAID_EVENT",
                  "orderId": 9001,
                  "userId": 77
                }
                """, AnalyticsOrderEventMessage.class);

        service.ingestOrderEvent(event);

        ArgumentCaptor<AnalyticsJourneyEvent> journeyCaptor = ArgumentCaptor.forClass(AnalyticsJourneyEvent.class);
        verify(analyticsJourneyEventRepository).save(journeyCaptor.capture());
        AnalyticsJourneyEvent saved = journeyCaptor.getValue();
        assertThat(saved.getEventType()).isEqualTo("ORDER_PAID_EVENT");
        assertThat(saved.getStoreId()).isEqualTo(100L);
        assertThat(saved.getSellerId()).isEqualTo(200L);
        assertThat(saved.getItemId()).isEqualTo(300L);
        assertThat(saved.getJourneyId()).isEqualTo("order-9001");
        assertThat(saved.getEventSequence()).isEqualTo(0);
    }

    @Test
    void ingestSalesEvent_duplicateRawEvent_skipsSave() {
        when(rawSalesEventRepository.findByEventId("evt-duplicate")).thenReturn(Optional.of(
                AnalyticsRawSalesEvent.builder()
                        .eventId("evt-duplicate")
                        .eventType("PURCHASE_CREATED")
                        .storeId(11L)
                        .sellerId(22L)
                        .itemId(33L)
                        .occurredAt(LocalDateTime.now())
                        .ingestedAt(LocalDateTime.now())
                        .build()
        ));

        AnalyticsSalesEventMessage event = JsonUtils.fromJson("""
                {
                  "eventId": "evt-duplicate",
                  "eventType": "PURCHASE_CREATED",
                  "storeId": 11,
                  "sellerId": 22,
                  "itemId": 33
                }
                """, AnalyticsSalesEventMessage.class);

        service.ingestSalesEvent(event);

        verify(rawSalesEventRepository, org.mockito.Mockito.never()).save(any(AnalyticsRawSalesEvent.class));
        verify(analyticsJourneyEventRepository, org.mockito.Mockito.never()).save(any(AnalyticsJourneyEvent.class));
    }
}
