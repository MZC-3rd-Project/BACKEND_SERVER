package com.example.order.domain;

import com.example.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusTest {

    @Nested
    @DisplayName("유효한 상태 전이")
    class ValidTransitions {

        @Test
        @DisplayName("PAYMENT_PENDING → PAID 전이 성공")
        void paymentPendingToPaid() {
            assertThat(OrderStatus.PAYMENT_PENDING.canTransitionTo(OrderStatus.PAID)).isTrue();
        }

        @Test
        @DisplayName("PAYMENT_PENDING → CANCELLED 전이 성공")
        void paymentPendingToCancelled() {
            assertThat(OrderStatus.PAYMENT_PENDING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("PAID → SHIPPING 전이 성공")
        void paidToShipping() {
            assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.SHIPPING)).isTrue();
        }

        @Test
        @DisplayName("PAID → REFUND_REQUESTED 전이 성공")
        void paidToRefundRequested() {
            assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.REFUND_REQUESTED)).isTrue();
        }

        @Test
        @DisplayName("SHIPPING → DELIVERED 전이 성공")
        void shippingToDelivered() {
            assertThat(OrderStatus.SHIPPING.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
        }

        @Test
        @DisplayName("DELIVERED → COMPLETED 전이 성공")
        void deliveredToCompleted() {
            assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.COMPLETED)).isTrue();
        }

        @Test
        @DisplayName("COMPLETED → REFUND_REQUESTED 전이 성공")
        void completedToRefundRequested() {
            assertThat(OrderStatus.COMPLETED.canTransitionTo(OrderStatus.REFUND_REQUESTED)).isTrue();
        }

        @Test
        @DisplayName("REFUND_REQUESTED → REFUNDED 전이 성공")
        void refundRequestedToRefunded() {
            assertThat(OrderStatus.REFUND_REQUESTED.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
        }
    }

    @Nested
    @DisplayName("무효한 상태 전이")
    class InvalidTransitions {

        @Test
        @DisplayName("CANCELLED → PAID 전이 실패")
        void cancelledToPaid() {
            assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.PAID)).isFalse();
        }

        @Test
        @DisplayName("REFUNDED → 어떤 상태로도 전이 불가")
        void refundedToAny() {
            for (OrderStatus target : OrderStatus.values()) {
                assertThat(OrderStatus.REFUNDED.canTransitionTo(target)).isFalse();
            }
        }

        @Test
        @DisplayName("PAYMENT_PENDING → COMPLETED 직접 전이 실패")
        void paymentPendingToCompleted() {
            assertThat(OrderStatus.PAYMENT_PENDING.canTransitionTo(OrderStatus.COMPLETED)).isFalse();
        }

        @Test
        @DisplayName("validateTransitionTo - 무효 전이 시 BusinessException 발생")
        void validateTransitionThrowsException() {
            assertThatThrownBy(() -> OrderStatus.CANCELLED.validateTransitionTo(OrderStatus.PAID))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
