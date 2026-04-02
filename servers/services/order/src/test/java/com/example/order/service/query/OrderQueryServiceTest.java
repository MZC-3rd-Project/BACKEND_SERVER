package com.example.order.service.query;

import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.order.domain.OrderRepository;
import com.example.order.domain.OrderStatus;
import com.example.order.dto.response.InternalOrderReviewEligibilityResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
    void getReviewEligibility_returnsTrue_whenOrderIsPaidAndContainsItem() {
        Order order = Order.create(1L, 100L, 10000L, null, null, null, null, null);
        order.addItem(OrderItem.create("NORMAL", null, 200L, 300L, 1, 10000L, 10000L));
        order.transitTo(OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        InternalOrderReviewEligibilityResponse response = orderQueryService.getReviewEligibility(1L, 100L, 200L);

        assertThat(response.isEligible()).isTrue();
    }

    @Test
    void getReviewEligibility_returnsFalse_whenOrderIsNotCompletedOrItemMissing() {
        Order order = Order.create(1L, 100L, 10000L, null, null, null, null, null);
        order.addItem(OrderItem.create("NORMAL", null, 200L, 300L, 1, 10000L, 10000L));
        order.transitTo(OrderStatus.PAID);
        order.transitTo(OrderStatus.SHIPPING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        InternalOrderReviewEligibilityResponse response = orderQueryService.getReviewEligibility(1L, 100L, 999L);

        assertThat(response.isEligible()).isFalse();
    }

    @Test
    void getMyOrders_appliesLatestOrderSortWhenPageableIsUnsorted() {
        Page<Order> page = new PageImpl<>(List.of());
        when(orderRepository.findByUserIdAndDeletedAtIsNull(eq(100L), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(page);

        orderQueryService.getMyOrders(100L, PageRequest.of(0, 20));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findByUserIdAndDeletedAtIsNull(eq(100L), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort()).isEqualTo(
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
    }
}
