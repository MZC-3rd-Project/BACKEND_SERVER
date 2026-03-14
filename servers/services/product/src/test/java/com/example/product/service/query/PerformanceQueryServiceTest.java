package com.example.product.service.query;

import com.example.core.exception.BusinessException;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.entity.item.ItemType;
import com.example.product.entity.performance.Performance;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.CastMemberRepository;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.PerformanceRepository;
import com.example.product.repository.SeatGradeRepository;
import com.example.product.service.content.ItemContentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceQueryServiceTest {

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
    private ItemContentService itemContentService;
    @Spy
    private ItemAccessPolicy itemAccessPolicy = new ItemAccessPolicy();

    @InjectMocks
    private PerformanceQueryService performanceQueryService;

    @Test
    void findById_whenItemTypeMismatch_throwsBusinessError() {
        Long itemId = 100L;
        Item goodsItem = Item.create("goods", "desc", 1000L, ItemType.GOODS, null, 1L, 1L, null);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(goodsItem));

        assertThatThrownBy(() -> performanceQueryService.findById(itemId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.ITEM_TYPE_MISMATCH);

        verifyNoInteractions(performanceRepository, seatGradeRepository, castMemberRepository, itemImageRepository);
    }

    @Test
    void findById_whenItemNotVisible_throwsItemNotFound() {
        Long itemId = 101L;
        Item hiddenPerformance = Item.create("performance", "desc", 1000L, ItemType.PERFORMANCE, null, 1L, 1L, null);
        ReflectionTestUtils.setField(hiddenPerformance, "status", ItemStatus.HIDDEN);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(hiddenPerformance));

        assertThatThrownBy(() -> performanceQueryService.findById(itemId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.ITEM_NOT_FOUND);

        verifyNoInteractions(performanceRepository, seatGradeRepository, castMemberRepository, itemImageRepository);
    }

    @Test
    void findSellerById_whenOwnerCanAccessHiddenItem_returnsDetail() {
        Long itemId = 102L;
        Long sellerId = 11L;
        Item hiddenPerformance = Item.create("performance", "desc", 1000L, ItemType.PERFORMANCE, null, sellerId, 1L, null);
        ReflectionTestUtils.setField(hiddenPerformance, "status", ItemStatus.HIDDEN);
        Performance performance = Performance.create(
                itemId,
                "hall",
                LocalDate.of(2030, 1, 1),
                LocalTime.of(18, 0),
                100,
                140,
                "15+",
                "Seoul",
                "No re-entry",
                "DonMoa Live",
                "DonMoa"
        );
        ReflectionTestUtils.setField(performance, "id", 500L);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(hiddenPerformance));
        when(performanceRepository.findByItemId(itemId)).thenReturn(Optional.of(performance));
        when(seatGradeRepository.findByPerformanceIdOrderByPriceDesc(500L)).thenReturn(List.of());
        when(castMemberRepository.findByPerformanceId(500L)).thenReturn(List.of());
        when(itemImageRepository.findByItemIdOrderBySortOrder(itemId)).thenReturn(List.of());

        var response = performanceQueryService.findSellerById(itemId, sellerId);

        assertThat(response.getStatus()).isEqualTo(ItemStatus.HIDDEN.name());
        assertThat(response.getRunningTimeMinutes()).isEqualTo(140);
        assertThat(response.getAgeLimit()).isEqualTo("15+");
        assertThat(response.getVenueAddress()).isEqualTo("Seoul");
        assertThat(response.getBookingNotice()).isEqualTo("No re-entry");
        assertThat(response.getOrganizer()).isEqualTo("DonMoa Live");
        assertThat(response.getHost()).isEqualTo("DonMoa");
    }
}
