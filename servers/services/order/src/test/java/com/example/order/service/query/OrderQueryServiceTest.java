package com.example.order.service.query;

import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.order.domain.OrderRepository;
import com.example.order.domain.OrderStatus;
import com.example.order.dto.response.InternalOrderReviewEligibilityResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderQueryService orderQueryService;

    @Test
    void getReviewEligibility_returnsTrue_whenDeliveredOrderContainsItem() {
        Order order = Order.create(1L, 100L, 10000L, null, null, null, null, null);
        order.addItem(OrderItem.create("NORMAL", null, 200L, 300L, 1, 10000L, 10000L));
        order.transitTo(OrderStatus.PAID);
        order.transitTo(OrderStatus.SHIPPING);
        order.transitTo(OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        InternalOrderReviewEligibilityResponse response = orderQueryService.getReviewEligibility(1L, 100L, 200L);

        assertThat(response.isEligible()).isTrue();
    }

    @Test
    void getReviewEligibility_returnsFalse_whenOrderIsNotCompletedOrItemMissing() {
        Order order = Order.create(1L, 100L, 10000L, null, null, null, null, null);
        order.addItem(OrderItem.create("NORMAL", null, 200L, 300L, 1, 10000L, 10000L));
        order.transitTo(OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        InternalOrderReviewEligibilityResponse response = orderQueryService.getReviewEligibility(1L, 100L, 999L);

        assertThat(response.isEligible()).isFalse();
    }
}
