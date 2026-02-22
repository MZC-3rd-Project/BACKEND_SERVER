package com.example.product.service.command;

import com.example.event.EventPublisher;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemType;
import com.example.product.entity.performance.Performance;
import com.example.product.repository.CastMemberRepository;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.PerformanceRepository;
import com.example.product.repository.SeatGradeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceCommandServiceTest {

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private PerformanceRepository performanceRepository;
    @Mock
    private SeatGradeRepository seatGradeRepository;
    @Mock
    private CastMemberRepository castMemberRepository;
    @Mock
    private ItemImageRepository itemImageRepository;
    @Mock
    private MediaReferenceService mediaReferenceService;
    @Mock
    private ItemMediaLinkSyncService itemMediaLinkSyncService;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private PerformanceCommandService performanceCommandService;

    @Test
    void delete_clearsItemMediaLinks() {
        Long itemId = 3L;
        Long sellerId = 10L;
        Item item = createItem(itemId, sellerId);
        Performance performance = createPerformance(100L, itemId);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(performanceRepository.findByItemId(itemId)).thenReturn(Optional.of(performance));

        performanceCommandService.delete(itemId, sellerId);

        verify(seatGradeRepository).softDeleteAllByPerformanceId(100L);
        verify(castMemberRepository).softDeleteAllByPerformanceId(100L);
        verify(performanceRepository).softDeleteByItemId(itemId);
        verify(itemImageRepository).softDeleteAllByItemId(itemId);
        verify(itemMediaLinkSyncService).clearAfterCommit(itemId);
        assertThat(item.getThumbnailMediaId()).isNull();
    }

    private Item createItem(Long id, Long sellerId) {
        Item item = Item.create(
                "performance",
                "desc",
                1000L,
                ItemType.PERFORMANCE,
                null,
                sellerId,
                100L,
                777L,
                "https://thumbnail"
        );
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private Performance createPerformance(Long id, Long itemId) {
        Performance performance = Performance.create(
                itemId,
                "hall",
                LocalDate.of(2030, 1, 1),
                LocalTime.of(19, 30),
                100
        );
        ReflectionTestUtils.setField(performance, "id", id);
        return performance;
    }
}
