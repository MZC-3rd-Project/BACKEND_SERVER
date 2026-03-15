package com.example.hotdeal.service.query;

import com.example.core.exception.BusinessException;
import com.example.hotdeal.entity.HotDeal;
import com.example.hotdeal.entity.HotDealStatus;
import com.example.hotdeal.exception.HotDealErrorCode;
import com.example.hotdeal.repository.HotDealRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotDealDetailReaderTest {

    @Mock
    private HotDealRepository hotDealRepository;

    private HotDealDetailReader hotDealDetailReader;

    @BeforeEach
    void setUp() {
        hotDealDetailReader = new HotDealDetailReader(hotDealRepository);
    }

    @Test
    void read_returnsMappedDetailResponse() {
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
        ReflectionTestUtils.setField(hotDeal, "createdAt", LocalDateTime.of(2026, 3, 14, 9, 0));
        when(hotDealRepository.findById(10L)).thenReturn(Optional.of(hotDeal));

        var view = hotDealDetailReader.read(10L);

        assertThat(view.id()).isEqualTo(10L);
        assertThat(view.itemId()).isEqualTo(501L);
        assertThat(view.title()).isEqualTo("Spring Sale");
        assertThat(view.remainingQuantity()).isEqualTo(35);
        assertThat(view.soldQuantity()).isEqualTo(15);
        assertThat(view.status()).isEqualTo(HotDealStatus.ACTIVE);
    }

    @Test
    void read_throwsWhenHotDealDoesNotExist() {
        when(hotDealRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hotDealDetailReader.read(10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(HotDealErrorCode.HOT_DEAL_NOT_FOUND);
    }
}
