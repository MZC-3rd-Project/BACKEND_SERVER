package com.example.hotdeal.entity;

import com.example.core.exception.BusinessException;
import com.example.hotdeal.exception.HotDealErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HotDealTest {

    @Test
    void create_initializesScheduledDealAndDerivedValues() {
        LocalDateTime startAt = LocalDateTime.of(2026, 3, 14, 10, 0);
        LocalDateTime endAt = startAt.plusHours(2);

        HotDeal hotDeal = HotDeal.create(
                101L,
                "Flash deal",
                20_000L,
                25,
                10,
                null,
                startAt,
                endAt
        );

        assertThat(hotDeal.getItemId()).isEqualTo(101L);
        assertThat(hotDeal.getTitle()).isEqualTo("Flash deal");
        assertThat(hotDeal.getOriginalPrice()).isEqualTo(20_000L);
        assertThat(hotDeal.getDiscountRate()).isEqualTo(25);
        assertThat(hotDeal.getDiscountedPrice()).isEqualTo(15_000L);
        assertThat(hotDeal.getStartAt()).isEqualTo(startAt);
        assertThat(hotDeal.getEndAt()).isEqualTo(endAt);
        assertThat(hotDeal.getMaxQuantity()).isEqualTo(10);
        assertThat(hotDeal.getMaxPerUser()).isEqualTo(1);
        assertThat(hotDeal.getSoldQuantity()).isZero();
        assertThat(hotDeal.getStatus()).isEqualTo(HotDealStatus.SCHEDULED);
        assertThat(hotDeal.getRemainingQuantity()).isEqualTo(10);
        assertThat(hotDeal.isSoldOut()).isFalse();
    }

    @Test
    void activate_thenEnd_followsAllowedStatusTransitions() {
        HotDeal hotDeal = newHotDeal();

        hotDeal.activate();
        hotDeal.end();

        assertThat(hotDeal.getStatus()).isEqualTo(HotDealStatus.ENDED);
    }

    @Test
    void cancel_isAllowedFromScheduledAndActive() {
        HotDeal scheduledDeal = newHotDeal();
        HotDeal activeDeal = newHotDeal();
        activeDeal.activate();

        scheduledDeal.cancel();
        activeDeal.cancel();

        assertThat(scheduledDeal.getStatus()).isEqualTo(HotDealStatus.CANCELLED);
        assertThat(activeDeal.getStatus()).isEqualTo(HotDealStatus.CANCELLED);
    }

    @Test
    void invalidTransitions_throwBusinessException() {
        HotDeal scheduledDeal = newHotDeal();
        HotDeal endedDeal = newHotDeal();
        endedDeal.activate();
        endedDeal.end();

        HotDeal cancelledDeal = newHotDeal();
        cancelledDeal.cancel();

        assertThatThrownBy(scheduledDeal::end)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(HotDealErrorCode.INVALID_STATUS_TRANSITION);

        assertThatThrownBy(endedDeal::activate)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(HotDealErrorCode.INVALID_STATUS_TRANSITION);

        assertThatThrownBy(cancelledDeal::activate)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(HotDealErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void incrementSoldQuantity_updatesRemainingQuantityAndSoldOutState() {
        HotDeal hotDeal = HotDeal.create(
                101L,
                "Flash deal",
                20_000L,
                10,
                5,
                2,
                LocalDateTime.of(2026, 3, 14, 10, 0),
                LocalDateTime.of(2026, 3, 14, 12, 0)
        );

        hotDeal.incrementSoldQuantity(2);

        assertThat(hotDeal.getSoldQuantity()).isEqualTo(2);
        assertThat(hotDeal.getRemainingQuantity()).isEqualTo(3);
        assertThat(hotDeal.isSoldOut()).isFalse();

        hotDeal.incrementSoldQuantity(3);

        assertThat(hotDeal.getSoldQuantity()).isEqualTo(5);
        assertThat(hotDeal.getRemainingQuantity()).isZero();
        assertThat(hotDeal.isSoldOut()).isTrue();
    }

    private HotDeal newHotDeal() {
        return HotDeal.create(
                101L,
                "Flash deal",
                20_000L,
                20,
                10,
                2,
                LocalDateTime.of(2026, 3, 14, 10, 0),
                LocalDateTime.of(2026, 3, 14, 12, 0)
        );
    }
}
