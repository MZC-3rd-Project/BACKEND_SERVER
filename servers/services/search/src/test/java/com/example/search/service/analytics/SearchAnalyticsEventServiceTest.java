package com.example.search.service.analytics;

import com.example.core.pagination.CursorResponse;
import com.example.event.DomainEvent;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.search.client.ProductSearchSourceClient;
import com.example.search.client.dto.ProductSearchDocument;
import com.example.search.dto.request.SearchClickTrackRequest;
import com.example.search.dto.response.SearchItemResponse;
import com.example.search.service.query.SearchQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SearchAnalyticsEventServiceTest {

    private EventPublisher eventPublisher;
    private ProductSearchSourceClient productSearchSourceClient;
    private SearchAnalyticsEventService searchAnalyticsEventService;

    @BeforeEach
    void setUp() {
        eventPublisher = mock(EventPublisher.class);
        productSearchSourceClient = mock(ProductSearchSourceClient.class);
        searchAnalyticsEventService = new SearchAnalyticsEventService(eventPublisher, productSearchSourceClient);
    }

    @Test
    void publishSearchExecuted_publishesExpectedPayload() {
        List<SearchItemResponse> items = LongStream.rangeClosed(1, 25)
                .mapToObj(itemId -> new SearchItemResponse(
                        itemId,
                        "item-" + itemId,
                        "PRODUCT",
                        "ON_SALE",
                        "NORMAL",
                        1000L,
                        1000L,
                        1000L,
                        null,
                        null,
                        null,
                        null,
                        null
                ))
                .toList();

        searchAnalyticsEventService.publishSearchExecuted(
                SearchQuery.of("런닝화", null, null, null, null, null, "LATEST", null, 20),
                CursorResponse.of(items, "next-cursor", 25L),
                SearchRequestContext.of(101L, "sess-abc", "journey-123", "corr-001", null)
        );

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        ArgumentCaptor<EventMetadata> metadataCaptor = ArgumentCaptor.forClass(EventMetadata.class);
        verify(eventPublisher).publish(eventCaptor.capture(), metadataCaptor.capture());

        DomainEvent event = eventCaptor.getValue();
        EventMetadata metadata = metadataCaptor.getValue();

        assertThat(event.getTopic()).isEqualTo("search-events");
        assertThat(event.getEventTypeName()).isEqualTo("SEARCH_EXECUTED");
        assertThat(event.getPayload())
                .containsEntry("queryHash", SearchAnalyticsEventService.hashQuery("런닝화"))
                .containsEntry("sessionId", "sess-abc")
                .containsEntry("userId", 101L)
                .containsEntry("journeyId", "journey-123")
                .containsEntry("correlationId", "corr-001")
                .containsEntry("causationId", null);
        @SuppressWarnings("unchecked")
        List<Long> resultItemIds = (List<Long>) event.getPayload().get("resultItemIds");

        assertThat(resultItemIds)
                .hasSize(20)
                .containsExactlyElementsOf(LongStream.rangeClosed(1, 20).boxed().toList());

        assertThat(metadata.aggregateType()).isEqualTo("SearchQuery");
        assertThat(metadata.aggregateId()).isEqualTo(SearchAnalyticsEventService.hashQuery("런닝화"));
        assertThat(metadata.correlationId()).isEqualTo("corr-001");
        assertThat(metadata.causationId()).isNull();
    }

    @Test
    void publishItemClicked_enrichesMissingOwnershipFromProductDocument() {
        when(productSearchSourceClient.findSearchDocument(3001L)).thenReturn(Optional.of(
                new ProductSearchDocument(
                        3001L,
                        "데이트 공연",
                        "상세 설명",
                        10L,
                        "공연",
                        List.of("문화", "공연"),
                        "PERFORMANCE",
                        "ON_SALE",
                        55000L,
                        1001L,
                        2001L,
                        999L,
                        List.of("데이트"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        30,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                )
        ));

        searchAnalyticsEventService.publishItemClicked(
                new SearchClickTrackRequest(
                        3001L,
                        "데이트 공연",
                        null,
                        null,
                        null,
                        null,
                        "evt-search-001",
                        null,
                        null
                ),
                SearchRequestContext.of(101L, "sess-abc", null, "corr-001", null)
        );

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        ArgumentCaptor<EventMetadata> metadataCaptor = ArgumentCaptor.forClass(EventMetadata.class);
        verify(eventPublisher).publish(eventCaptor.capture(), metadataCaptor.capture());

        DomainEvent event = eventCaptor.getValue();
        EventMetadata metadata = metadataCaptor.getValue();

        assertThat(event.getTopic()).isEqualTo("search-events");
        assertThat(event.getEventTypeName()).isEqualTo("SEARCH_ITEM_CLICKED");
        assertThat(event.getPayload())
                .containsEntry("itemId", 3001L)
                .containsEntry("storeId", 2001L)
                .containsEntry("sellerId", 1001L)
                .containsEntry("queryHash", SearchAnalyticsEventService.hashQuery("데이트 공연"))
                .containsEntry("sessionId", "sess-abc")
                .containsEntry("userId", 101L)
                .containsEntry("correlationId", "corr-001")
                .containsEntry("causationId", "evt-search-001");

        assertThat(metadata.aggregateType()).isEqualTo("SearchItem");
        assertThat(metadata.aggregateId()).isEqualTo("3001");
        assertThat(metadata.correlationId()).isEqualTo("corr-001");
        assertThat(metadata.causationId()).isEqualTo("evt-search-001");
    }

    @Test
    void publishSearchExecuted_skipsWhenUserIdMissing() {
        searchAnalyticsEventService.publishSearchExecuted(
                SearchQuery.of("런닝화", null, null, null, null, null, "LATEST", null, 20),
                CursorResponse.of(List.of(), null, 0L),
                SearchRequestContext.of(null, "sess-guest", null, "corr-001", null)
        );

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void publishItemClicked_skipsWhenUserIdMissing() {
        searchAnalyticsEventService.publishItemClicked(
                new SearchClickTrackRequest(
                        3001L,
                        "데이트 공연",
                        null,
                        "sess-guest",
                        null,
                        "corr-001",
                        null,
                        null,
                        null
                ),
                SearchRequestContext.of(null, "sess-guest", null, "corr-001", null)
        );

        verifyNoInteractions(eventPublisher);
        verifyNoInteractions(productSearchSourceClient);
    }
}
