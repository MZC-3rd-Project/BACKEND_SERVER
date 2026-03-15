package com.example.hotdeal.service;

import com.example.hotdeal.dto.query.response.HotDealListQueryResponse;
import com.example.hotdeal.entity.HotDeal;
import com.example.hotdeal.entity.HotDealStatus;
import com.example.hotdeal.dto.query.response.HotDealDetailQueryResponse;
import com.example.hotdeal.repository.HotDealRepository;
import com.example.hotdeal.service.query.HotDealDetailCache;
import com.example.hotdeal.service.query.HotDealDetailReader;
import com.example.hotdeal.service.query.HotDealQueryAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotDealQueryServiceTest {

    @Mock
    private HotDealRepository hotDealRepository;

    @Mock
    private HotDealDetailCache hotDealDetailCache;

    @Mock
    private HotDealDetailReader hotDealDetailReader;

    private HotDealQueryAssembler hotDealQueryAssembler;

    private HotDealQueryService hotDealQueryService;

    @BeforeEach
    void setUp() {
        hotDealQueryAssembler = new HotDealQueryAssembler();
        hotDealQueryService = new HotDealQueryService(
                hotDealRepository,
                hotDealDetailCache,
                hotDealDetailReader,
                hotDealQueryAssembler
        );
    }

    @Test
    void getDetail_returnsCachedResponseWithoutReadingRepository() {
        HotDealDetailQueryResponse response = HotDealDetailQueryResponse.builder()
                .id(10L)
                .title("cached")
                .build();
        when(hotDealDetailCache.get(10L)).thenReturn(Optional.of(response));

        HotDealDetailQueryResponse result = hotDealQueryService.getDetail(10L);

        assertThat(result).isSameAs(response);
        verify(hotDealDetailReader, never()).read(10L);
        verify(hotDealDetailCache, never()).put(10L, response);
    }

    @Test
    void getDetail_readsAndCachesWhenCacheMisses() {
        when(hotDealDetailCache.get(10L)).thenReturn(Optional.empty());
        when(hotDealDetailReader.read(10L)).thenReturn(new com.example.hotdeal.service.query.HotDealDetailView(
                10L,
                501L,
                "fresh",
                10000L,
                20,
                8000L,
                50,
                2,
                15,
                35,
                com.example.hotdeal.entity.HotDealStatus.ACTIVE,
                java.time.LocalDateTime.of(2026, 3, 14, 10, 0),
                java.time.LocalDateTime.of(2026, 3, 14, 12, 0),
                java.time.LocalDateTime.of(2026, 3, 14, 9, 0)
        ));

        HotDealDetailQueryResponse result = hotDealQueryService.getDetail(10L);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getTitle()).isEqualTo("fresh");
        assertThat(result.getProgressRate()).isEqualTo(30.0);
        verify(hotDealDetailReader).read(10L);
        verify(hotDealDetailCache).put(10L, result);
    }

    @Test
    void getActiveDeals_usesMaxCursorAndMapsListResponses() {
        HotDeal hotDeal = HotDeal.create(
                501L,
                "Spring Sale",
                10000L,
                20,
                50,
                2,
                LocalDateTime.of(2026, 3, 14, 10, 0),
                LocalDateTime.of(2026, 3, 14, 12, 0)
        );
        ReflectionTestUtils.setField(hotDeal, "id", 10L);
        ReflectionTestUtils.setField(hotDeal, "soldQuantity", 15);
        ReflectionTestUtils.setField(hotDeal, "status", HotDealStatus.ACTIVE);

        when(hotDealRepository.findByStatusWithCursor(
                eq(HotDealStatus.ACTIVE),
                eq(Long.MAX_VALUE),
                any(Pageable.class)
        )).thenReturn(List.of(hotDeal));

        List<HotDealListQueryResponse> responses = hotDealQueryService.getActiveDeals(null, 20);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getId()).isEqualTo(10L);
        assertThat(responses.getFirst().getTitle()).isEqualTo("Spring Sale");
        assertThat(responses.getFirst().getDiscountedPrice()).isEqualTo(8000L);
        verify(hotDealRepository).findByStatusWithCursor(
                eq(HotDealStatus.ACTIVE),
                eq(Long.MAX_VALUE),
                any(Pageable.class)
        );
    }
}
