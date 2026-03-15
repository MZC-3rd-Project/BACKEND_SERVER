package com.example.product.service.query;

import com.example.core.exception.BusinessException;
import com.example.product.entity.category.Category;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.entity.item.ItemType;
import com.example.product.entity.performance.Performance;
import com.example.product.entity.performance.SeatGrade;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.CategoryRepository;
import com.example.product.repository.CastMemberRepository;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.PerformanceRepository;
import com.example.product.repository.SeatGradeRepository;
import com.example.product.service.content.ItemContentService;
import com.example.product.service.query.assembler.PerformanceDetailAssembler;
import com.example.product.service.query.assembler.PerformanceListAssembler;
import com.example.product.service.query.detail.ItemCategoryDetailResolver;
import com.example.product.service.query.detail.PerformanceItemDetailReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
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
    private CategoryRepository categoryRepository;
    @Mock
    private CastMemberRepository castMemberRepository;
    @Mock
    private ItemImageRepository itemImageRepository;
    @Mock
    private ItemContentService itemContentService;
    @Spy
    private ItemAccessPolicy itemAccessPolicy = new ItemAccessPolicy();
    @Spy
    private PerformanceDetailAssembler performanceDetailAssembler = new PerformanceDetailAssembler();
    @Spy
    private PerformanceListAssembler performanceListAssembler = new PerformanceListAssembler();
    private ItemCategoryDetailResolver itemCategoryDetailResolver;
    private PerformanceItemDetailReader performanceItemDetailReader;
    private PerformanceQueryService performanceQueryService;

    @BeforeEach
    void setUp() {
        itemCategoryDetailResolver = new ItemCategoryDetailResolver(categoryRepository);
        performanceItemDetailReader = new PerformanceItemDetailReader(
                performanceRepository,
                seatGradeRepository,
                castMemberRepository,
                itemImageRepository,
                itemContentService,
                itemCategoryDetailResolver
        );
        performanceQueryService = new PerformanceQueryService(
                itemRepository,
                performanceRepository,
                itemImageRepository,
                itemAccessPolicy,
                performanceItemDetailReader,
                performanceDetailAssembler,
                performanceListAssembler
        );
    }

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
        Item hiddenPerformance = Item.create("performance", "desc", 1000L, ItemType.PERFORMANCE, 220L, sellerId, 1L, null);
        ReflectionTestUtils.setField(hiddenPerformance, "id", itemId);
        ReflectionTestUtils.setField(hiddenPerformance, "status", ItemStatus.HIDDEN);
        Category rootCategory = Category.createRoot("Culture", 1);
        ReflectionTestUtils.setField(rootCategory, "id", 120L);
        Category childCategory = Category.createChild("Musical", 120L, 0, 1);
        ReflectionTestUtils.setField(childCategory, "id", 220L);
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
        SeatGrade vip = SeatGrade.create(500L, "VIP", 1500L, 10, 0);
        SeatGrade r = SeatGrade.create(500L, "R", 3000L, 10, 0);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(hiddenPerformance));
        when(performanceRepository.findByItemIdIn(List.of(itemId))).thenReturn(List.of(performance));
        when(seatGradeRepository.findByPerformanceIdIn(List.of(500L))).thenReturn(List.of(vip, r));
        when(castMemberRepository.findByPerformanceIdIn(List.of(500L))).thenReturn(List.of());
        when(itemImageRepository.findByItemIdInOrderByItemIdAscSortOrderAsc(List.of(itemId))).thenReturn(List.of());
        when(itemContentService.findByItemIds(List.of(itemId))).thenReturn(Map.of());
        when(categoryRepository.findAllByOrderByDepthAscSortOrderAsc()).thenReturn(List.of(rootCategory, childCategory));

        var response = performanceQueryService.findSellerById(itemId, sellerId);

        assertThat(response.getStatus()).isEqualTo(ItemStatus.HIDDEN.name());
        assertThat(response.getCategoryName()).isEqualTo("Musical");
        assertThat(response.getCategoryPath()).containsExactly("Culture", "Musical");
        assertThat(response.getPriceMeta()).isNotNull();
        assertThat(response.getPriceMeta().getStartingPrice()).isEqualTo(1500L);
        assertThat(response.getPriceMeta().getMinPrice()).isEqualTo(1500L);
        assertThat(response.getPriceMeta().getMaxPrice()).isEqualTo(3000L);
        assertThat(response.getRunningTimeMinutes()).isEqualTo(140);
        assertThat(response.getAgeLimit()).isEqualTo("15+");
        assertThat(response.getVenueAddress()).isEqualTo("Seoul");
        assertThat(response.getBookingNotice()).isEqualTo("No re-entry");
        assertThat(response.getOrganizer()).isEqualTo("DonMoa Live");
        assertThat(response.getHost()).isEqualTo("DonMoa");
    }

    @Test
    void findList_returnsAssembledResponses() {
        Item visiblePerformance = Item.create("performance", "desc", 2000L, ItemType.PERFORMANCE, null, 1L, 1L, null);
        ReflectionTestUtils.setField(visiblePerformance, "id", 302L);
        ReflectionTestUtils.setField(visiblePerformance, "status", ItemStatus.FUNDING);
        Performance performance = Performance.create(
                302L,
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

        when(itemRepository.findByItemTypeAndStatusIn(ItemType.PERFORMANCE, itemAccessPolicy.visibleStatuses(), org.springframework.data.domain.PageRequest.of(0, 2)))
                .thenReturn(List.of(visiblePerformance));
        when(performanceRepository.findByItemIdIn(List.of(302L))).thenReturn(List.of(performance));
        when(itemImageRepository.findByItemIdInOrderByItemIdAscSortOrderAsc(List.of(302L))).thenReturn(List.of());

        var response = performanceQueryService.findList(null, 1);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().getFirst().getId()).isEqualTo(302L);
        assertThat(response.getItems().getFirst().getVenue()).isEqualTo("hall");
        assertThat(response.getNextCursor()).isNull();
    }
}
