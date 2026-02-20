package com.example.search.service.index;

import com.example.search.consumer.ItemEventMessage;
import com.example.search.dto.index.response.IndexingFailureRetryResponse;
import com.example.search.entity.SearchIndexingFailure;
import com.example.search.entity.SearchIndexingFailureStatus;
import com.example.search.repository.SearchIndexingFailureRepository;
import com.example.search.service.query.cache.SearchResultCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchIndexingFailureServiceTest {

    @Mock
    private SearchIndexingFailureRepository failureRepository;

    @Mock
    private SearchIndexingService searchIndexingService;

    @Mock
    private SearchResultCacheService searchResultCacheService;

    private SearchIndexingFailureService searchIndexingFailureService;

    @BeforeEach
    void setUp() {
        searchIndexingFailureService = new SearchIndexingFailureService(
                failureRepository,
                searchIndexingService,
                searchResultCacheService
        );
    }

    @Test
    void recordItemEventFailure_savesPendingFailure() {
        ItemEventMessage event = new ItemEventMessage();
        setField(event, "eventId", "evt-1");
        setField(event, "eventType", "ITEM_UPDATED");
        setField(event, "itemId", 101L);

        when(failureRepository.findByEventIdAndEventType("evt-1", "ITEM_UPDATED")).thenReturn(Optional.empty());
        when(failureRepository.save(any(SearchIndexingFailure.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        searchIndexingFailureService.recordItemEventFailure(event, "{\"itemId\":101}", new RuntimeException("boom"));

        ArgumentCaptor<SearchIndexingFailure> captor = ArgumentCaptor.forClass(SearchIndexingFailure.class);
        verify(failureRepository).save(captor.capture());
        SearchIndexingFailure saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo("evt-1");
        assertThat(saved.getEventType()).isEqualTo("ITEM_UPDATED");
        assertThat(saved.getStatus()).isEqualTo(SearchIndexingFailureStatus.PENDING);
        assertThat(saved.getFailureReason()).isEqualTo("boom");
    }

    @Test
    void retryFailure_replaysAndMarksResolved() {
        SearchIndexingFailure failure = SearchIndexingFailure.builder()
                .eventId("evt-2")
                .eventType("ITEM_UPDATED")
                .itemId(101L)
                .payload("""
                        {
                          "eventId": "evt-2",
                          "eventType": "ITEM_UPDATED",
                          "itemId": 101,
                          "title": "아이폰 케이스 2",
                          "price": 25000
                        }
                        """)
                .retryCount(0)
                .status(SearchIndexingFailureStatus.PENDING)
                .failureReason("initial")
                .build();

        when(failureRepository.findById(1L)).thenReturn(Optional.of(failure));
        when(failureRepository.save(any(SearchIndexingFailure.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IndexingFailureRetryResponse response = searchIndexingFailureService.retryFailure(1L);

        verify(searchIndexingService).updateItem(101L, "아이폰 케이스 2", 25000L);
        verify(searchResultCacheService).evictAll();
        assertThat(response.getStatus()).isEqualTo(SearchIndexingFailureStatus.RESOLVED);
        assertThat(response.getRetryCount()).isEqualTo(1);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
