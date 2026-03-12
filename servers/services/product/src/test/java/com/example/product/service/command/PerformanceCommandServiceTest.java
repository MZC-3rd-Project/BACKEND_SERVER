package com.example.product.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventPublisher;
import com.example.product.dto.performance.request.PerformanceCreateRequest;
import com.example.product.dto.performance.request.PerformanceUpdateRequest;
import com.example.product.dto.performance.response.PerformanceDetailResponse;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemType;
import com.example.product.entity.performance.Performance;
import com.example.product.event.ItemDeletedEvent;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.CategoryRepository;
import com.example.product.repository.CastMemberRepository;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.PerformanceRepository;
import com.example.product.repository.SeatGradeRepository;
import com.example.product.service.command.image.ItemThumbnailSyncService;
import com.example.product.service.command.image.MediaReferenceService;
import com.example.product.service.content.ItemContentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceCommandServiceTest {

    @Mock
    private CategoryRepository categoryRepository;
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
    private ItemThumbnailSyncService itemThumbnailSyncService;
    @Mock
    private StoreOwnershipValidator storeOwnershipValidator;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private ItemContentService itemContentService;

    @InjectMocks
    private PerformanceCommandService performanceCommandService;

    @Test
    void delete_clearsItemMediaLinks() {
        Long itemId = 3L;
        Long sellerId = 10L;
        Item item = createItem(itemId, sellerId);
        Performance performance = createPerformance(100L, itemId);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));
        when(performanceRepository.findByItemId(itemId)).thenReturn(Optional.of(performance));

        performanceCommandService.delete(itemId, sellerId);

        verify(seatGradeRepository).softDeleteAllByPerformanceId(100L);
        verify(castMemberRepository).softDeleteAllByPerformanceId(100L);
        verify(performanceRepository).softDeleteByItemId(itemId);
        verify(itemImageRepository).softDeleteAllByItemId(itemId);
        verify(itemThumbnailSyncService).syncAfterCommit(itemId, null, true);
        verify(eventPublisher).publish(any(ItemDeletedEvent.class), any());
        assertThat(item.getThumbnailMediaId()).isNull();
    }

    @Test
    void delete_whenItemTypeMismatch_throwsBusinessError() {
        Long itemId = 3L;
        Long sellerId = 10L;
        Item goodsItem = createItem(itemId, sellerId, ItemType.GOODS);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(goodsItem));

        assertThatThrownBy(() -> performanceCommandService.delete(itemId, sellerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.ITEM_TYPE_MISMATCH);

        verifyNoInteractions(seatGradeRepository, castMemberRepository, performanceRepository, itemImageRepository);
        verifyNoInteractions(itemThumbnailSyncService);
    }

    @Test
    void create_whenCategoryNotFound_throwsBusinessError() {
        PerformanceCreateRequest request = new PerformanceCreateRequest();
        ReflectionTestUtils.setField(request, "title", "performance");
        ReflectionTestUtils.setField(request, "description", "desc");
        ReflectionTestUtils.setField(request, "price", 1000L);
        ReflectionTestUtils.setField(request, "storeId", 1L);
        ReflectionTestUtils.setField(request, "categoryId", 999L);
        ReflectionTestUtils.setField(request, "venue", "hall");
        ReflectionTestUtils.setField(request, "performanceDate", LocalDate.of(2030, 1, 1));
        ReflectionTestUtils.setField(request, "performanceTime", LocalTime.of(18, 0));
        ReflectionTestUtils.setField(request, "totalSeats", 100);
        ReflectionTestUtils.setField(request, "seatGrades", List.of());

        when(categoryRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> performanceCommandService.create(request, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.CATEGORY_NOT_FOUND);

        verifyNoInteractions(itemRepository, performanceRepository, seatGradeRepository, castMemberRepository, eventPublisher);
    }

    @Test
    void update_whenPartialRequest_preservesPerformanceFields() {
        Long itemId = 3L;
        Long sellerId = 10L;
        Item item = createItem(itemId, sellerId);
        Performance performance = createPerformance(100L, itemId);
        LocalDate originalDate = performance.getPerformanceDate();
        LocalTime originalTime = performance.getPerformanceTime();
        Integer originalTotalSeats = performance.getTotalSeats();
        String originalVenue = performance.getVenue();

        PerformanceUpdateRequest request = new PerformanceUpdateRequest();
        ReflectionTestUtils.setField(request, "title", "updated-title");

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));
        when(performanceRepository.findByItemId(itemId)).thenReturn(Optional.of(performance));
        when(seatGradeRepository.findByPerformanceIdOrderByPriceDesc(performance.getId())).thenReturn(List.of());
        when(castMemberRepository.findByPerformanceId(performance.getId())).thenReturn(List.of());
        when(itemImageRepository.findByItemIdOrderBySortOrder(itemId)).thenReturn(List.of());

        PerformanceDetailResponse response = performanceCommandService.update(itemId, request, sellerId);

        assertThat(response.getTitle()).isEqualTo("updated-title");
        assertThat(performance.getVenue()).isEqualTo(originalVenue);
        assertThat(performance.getPerformanceDate()).isEqualTo(originalDate);
        assertThat(performance.getPerformanceTime()).isEqualTo(originalTime);
        assertThat(performance.getTotalSeats()).isEqualTo(originalTotalSeats);
        verify(eventPublisher).publish(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private Item createItem(Long id, Long sellerId) {
        return createItem(id, sellerId, ItemType.PERFORMANCE);
    }

    private Item createItem(Long id, Long sellerId, ItemType itemType) {
        Item item = Item.create(
                "performance",
                "desc",
                1000L,
                itemType,
                null,
                sellerId,
                100L,
                777L
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
