package com.example.order.domain;

import com.example.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusTest {

    @ParameterizedTest
    @CsvSource({
            "PAYMENT_PENDING, PAID",
            "PAYMENT_PENDING, CANCELLED",
            "PAID, SHIPPING",
            "PAID, REFUND_REQUESTED",
            "SHIPPING, DELIVERED",
            "DELIVERED, COMPLETED",
            "COMPLETED, REFUND_REQUESTED",
            "REFUND_REQUESTED, REFUNDED"
    })
    @DisplayName("유효한 상태 전이는 성공한다")
    void validTransition_succeeds(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "CANCELLED, PAID",
            "CANCELLED, PAYMENT_PENDING",
            "REFUNDED, PAID",
            "PAYMENT_PENDING, SHIPPING",
            "PAID, CANCELLED",
            "SHIPPING, PAID",
            "DELIVERED, SHIPPING",
            "COMPLETED, PAID"
    })
    @DisplayName("유효하지 않은 상태 전이는 예외를 던진다")
    void invalidTransition_throwsException(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
        assertThatThrownBy(() -> from.validateTransitionTo(to))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("PAYMENT_PENDING에서 PAID로 전이 가능하다")
    void paymentPendingToPaid() {
        OrderStatus status = OrderStatus.PAYMENT_PENDING;
        assertThat(status.canTransitionTo(OrderStatus.PAID)).isTrue();
    }
}
