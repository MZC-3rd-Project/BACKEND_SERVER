package com.example.order.consumer.payment;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.EventPublisher;
import com.example.order.domain.Order;
import com.example.order.domain.OrderRepository;
import com.example.order.domain.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderPaymentEventProcessorTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    private OrderPaymentEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new OrderPaymentEventProcessor(orderRepository, eventPublisher, idempotentConsumerService);
    }

    @Test
    @DisplayName("PAYMENT_COMPLETED 이벤트 수신 시 Order 상태가 PAID로 전이된다")
    void paymentCompleted_orderBecomesPaid() {
        // given
        Order order = Order.create(101L, 100L, 50000L, null, null, null, null, null);
        when(orderRepository.findById(101L)).thenReturn(Optional.of(order));
        when(idempotentConsumerService.executeIdempotent(eq("evt-1"), eq("PAYMENT_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    supplier.get();
                    return Optional.empty();
                });

        String message = """
                {"eventId":"evt-1","eventType":"PAYMENT_COMPLETED","orderId":101,"userId":100}
                """;

        // when
        processor.process(message, "evt-1", "PAYMENT_COMPLETED");

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("PAYMENT_FAILED 이벤트 수신 시 Order 상태가 CANCELLED로 전이된다")
    void paymentFailed_orderBecomesCancelled() {
        // given
        Order order = Order.create(102L, 100L, 50000L, null, null, null, null, null);
        when(orderRepository.findById(102L)).thenReturn(Optional.of(order));
        when(idempotentConsumerService.executeIdempotent(eq("evt-2"), eq("PAYMENT_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    supplier.get();
                    return Optional.empty();
                });

        String message = """
                {"eventId":"evt-2","eventType":"PAYMENT_FAILED","orderId":102,"userId":100,"failReason":"잔액 부족"}
                """;

        // when
        processor.process(message, "evt-2", "PAYMENT_FAILED");

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("PAYMENT_TIMED_OUT 이벤트 수신 시 Order 상태가 CANCELLED로 전이된다")
    void paymentTimedOut_orderBecomesCancelled() {
        // given
        Order order = Order.create(103L, 100L, 50000L, null, null, null, null, null);
        when(orderRepository.findById(103L)).thenReturn(Optional.of(order));
        when(idempotentConsumerService.executeIdempotent(eq("evt-3"), eq("PAYMENT_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    supplier.get();
                    return Optional.empty();
                });

        String message = """
                {"eventId":"evt-3","eventType":"PAYMENT_TIMED_OUT","orderId":103,"userId":100}
                """;

        // when
        processor.process(message, "evt-3", "PAYMENT_TIMED_OUT");

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("중복 이벤트 수신 시 멱등성 검증 - 두 번째는 스킵된다")
    void duplicateEvent_isSkipped() {
        // given
        when(idempotentConsumerService.executeIdempotent(eq("evt-dup"), eq("PAYMENT_EVENT"), any()))
                .thenReturn(Optional.empty()); // 이미 처리됨 → supplier 호출하지 않음

        String message = """
                {"eventId":"evt-dup","eventType":"PAYMENT_COMPLETED","orderId":104,"userId":100}
                """;

        // when
        processor.process(message, "evt-dup", "PAYMENT_COMPLETED");

        // then
        verify(orderRepository, never()).findById(any());
    }

    @Test
    @DisplayName("orderId가 없는 이벤트는 스킵된다")
    void missingOrderId_isSkipped() {
        String message = """
                {"eventId":"evt-no-order","eventType":"PAYMENT_COMPLETED","userId":100}
                """;

        processor.process(message, "evt-no-order", "PAYMENT_COMPLETED");

        verifyNoInteractions(idempotentConsumerService);
        verify(orderRepository, never()).findById(any());
    }

    @Test
    @DisplayName("주문을 찾을 수 없으면 로그 경고 후 스킵한다")
    void orderNotFound_isSkipped() {
        // given
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());
        when(idempotentConsumerService.executeIdempotent(eq("evt-missing"), eq("PAYMENT_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    supplier.get();
                    return Optional.empty();
                });

        String message = """
                {"eventId":"evt-missing","eventType":"PAYMENT_COMPLETED","orderId":999,"userId":100}
                """;

        // when - should not throw
        processor.process(message, "evt-missing", "PAYMENT_COMPLETED");

        // then
        verify(orderRepository).findById(999L);
    }

    @Test
    @DisplayName("PAYMENT_REFUNDED 이벤트 수신 시 Order 상태가 REFUNDED로 전이되고 ORDER_REFUNDED_EVENT가 발행된다")
    void paymentRefunded_orderBecomesRefunded() {
        // given
        Order order = Order.create(105L, 100L, 50000L, null, null, null, null, null);
        order.transitTo(OrderStatus.PAID);
        order.transitTo(OrderStatus.REFUND_REQUESTED);
        when(orderRepository.findById(105L)).thenReturn(Optional.of(order));
        when(idempotentConsumerService.executeIdempotent(eq("evt-refund"), eq("PAYMENT_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    supplier.get();
                    return Optional.empty();
                });

        String message = """
                {"eventId":"evt-refund","eventType":"PAYMENT_REFUNDED","orderId":105,"userId":100}
                """;

        // when
        processor.process(message, "evt-refund", "PAYMENT_REFUNDED");

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        verify(eventPublisher).publish(any(), any());
    }
}
