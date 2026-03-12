package com.example.order.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventPublisher;
import com.example.order.domain.Order;
import com.example.order.domain.OrderRepository;
import com.example.order.domain.OrderStatus;
import com.example.order.dto.request.InternalCreateOrderRequest;
import com.example.order.dto.response.InternalCreateOrderResponse;
import com.example.order.exception.OrderErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCommandServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private EventPublisher eventPublisher;

    private OrderCommandService orderCommandService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        orderCommandService = new OrderCommandService(orderRepository, eventPublisher);
    }

    @Test
    @DisplayName("정상적인 주문 생성 요청 시 PAYMENT_PENDING 상태로 생성된다")
    void createOrder_success() throws Exception {
        // given
        String json = """
                {
                    "orderId": 12345,
                    "userId": 100,
                    "totalAmount": 50000,
                    "recipientName": "홍길동",
                    "recipientPhone": "010-1234-5678",
                    "deliveryAddressId": 1,
                    "deliveryMemo": "문 앞에 놓아주세요",
                    "lineItems": [
                        {
                            "channelType": "NORMAL",
                            "itemId": 1001,
                            "storeId": 10,
                            "quantity": 2,
                            "finalUnitPrice": 25000,
                            "lineAmount": 50000
                        }
                    ]
                }
                """;
        InternalCreateOrderRequest request = objectMapper.readValue(json, InternalCreateOrderRequest.class);
        when(orderRepository.existsById(12345L)).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        InternalCreateOrderResponse response = orderCommandService.createOrder(request);

        // then
        assertThat(response.getOrderId()).isEqualTo(12345L);
        assertThat(response.getStatus()).isEqualTo("PAYMENT_PENDING");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order savedOrder = captor.getValue();
        assertThat(savedOrder.getId()).isEqualTo(12345L);
        assertThat(savedOrder.getUserId()).isEqualTo(100L);
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(savedOrder.getOrderItems()).hasSize(1);
    }

    @Test
    @DisplayName("중복 orderId로 주문 생성 시 409 에러가 발생한다")
    void createOrder_duplicateOrderId_throws409() throws Exception {
        // given
        String json = """
                {
                    "orderId": 12345,
                    "userId": 100,
                    "totalAmount": 50000,
                    "lineItems": [
                        {
                            "channelType": "NORMAL",
                            "itemId": 1001,
                            "storeId": 10,
                            "quantity": 1,
                            "finalUnitPrice": 50000,
                            "lineAmount": 50000
                        }
                    ]
                }
                """;
        InternalCreateOrderRequest request = objectMapper.readValue(json, InternalCreateOrderRequest.class);
        when(orderRepository.existsById(12345L)).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> orderCommandService.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_ALREADY_EXISTS));

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("PAYMENT_PENDING 상태에서 주문 취소가 성공한다")
    void cancelOrder_success() {
        // given
        Order order = Order.create(12345L, 100L, 50000L, null, null, null, null, null);
        when(orderRepository.findById(12345L)).thenReturn(Optional.of(order));

        // when
        orderCommandService.cancelOrder(12345L, 100L);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(eventPublisher).publish(any(), any());
    }

    @Test
    @DisplayName("PAID 상태에서 주문 취소 시 예외가 발생한다")
    void cancelOrder_notCancellable_throwsException() {
        // given
        Order order = Order.create(12345L, 100L, 50000L, null, null, null, null, null);
        order.transitTo(OrderStatus.PAID);
        when(orderRepository.findById(12345L)).thenReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderCommandService.cancelOrder(12345L, 100L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_NOT_CANCELLABLE));
    }

    @Test
    @DisplayName("타인의 주문 취소 시 ORDER_NOT_FOUND 예외가 발생한다")
    void cancelOrder_otherUser_throwsNotFound() {
        // given
        Order order = Order.create(12345L, 100L, 50000L, null, null, null, null, null);
        when(orderRepository.findById(12345L)).thenReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderCommandService.cancelOrder(12345L, 999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));
    }
}
