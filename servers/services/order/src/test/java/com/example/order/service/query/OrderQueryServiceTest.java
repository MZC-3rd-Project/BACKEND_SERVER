package com.example.order.service.query;

import com.example.core.exception.BusinessException;
import com.example.order.domain.Order;
import com.example.order.domain.OrderRepository;
import com.example.order.dto.response.OrderDetailResponse;
import com.example.order.exception.OrderErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    @InjectMocks
    private OrderQueryService orderQueryService;

    @Mock
    private OrderRepository orderRepository;

    @Test
    @DisplayName("본인 주문 조회 성공")
    void getOrderDetailSuccess() {
        // given
        Order order = Order.create(1L, 100L, 50000L, "홍길동", "010-1234-5678", null, null, null);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        // when
        OrderDetailResponse response = orderQueryService.getOrderDetail(1L, 100L);

        // then
        assertThat(response.getOrderId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo(100L);
        assertThat(response.getStatus()).isEqualTo("PAYMENT_PENDING");
    }

    @Test
    @DisplayName("타인 주문 조회 시 ORDER_NOT_FOUND")
    void getOrderDetailNotOwner() {
        // given
        Order order = Order.create(1L, 100L, 50000L, null, null, null, null, null);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderQueryService.getOrderDetail(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("존재하지 않는 주문 조회 시 ORDER_NOT_FOUND")
    void getOrderDetailNotFound() {
        // given
        given(orderRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderQueryService.getOrderDetail(999L, 100L))
                .isInstanceOf(BusinessException.class);
    }
}
