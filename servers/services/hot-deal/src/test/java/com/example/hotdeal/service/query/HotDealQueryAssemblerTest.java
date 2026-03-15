package com.example.hotdeal.service.query;

import com.example.hotdeal.entity.HotDeal;
import com.example.hotdeal.entity.HotDealStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class HotDealQueryAssemblerTest {

    private final HotDealQueryAssembler hotDealQueryAssembler = new HotDealQueryAssembler();

    @Test
    void toListResponse_mapsHotDealFields() {
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

        var response = hotDealQueryAssembler.toListResponse(hotDeal);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getTitle()).isEqualTo("Spring Sale");
        assertThat(response.getDiscountedPrice()).isEqualTo(8000L);
        assertThat(response.getSoldQuantity()).isEqualTo(15);
    }

    @Test
    void toDetailResponse_computesProgressAndStatus() {
        HotDealDetailView view = new HotDealDetailView(
                10L,
                501L,
                "Spring Sale",
                10000L,
                20,
                8000L,
                50,
                2,
                15,
                35,
                HotDealStatus.ACTIVE,
                LocalDateTime.of(2026, 3, 14, 10, 0),
                LocalDateTime.of(2026, 3, 14, 12, 0),
                LocalDateTime.of(2026, 3, 14, 9, 0)
        );

        var response = hotDealQueryAssembler.toDetailResponse(view);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getRemainingQuantity()).isEqualTo(35);
        assertThat(response.getProgressRate()).isEqualTo(30.0);
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }
}
