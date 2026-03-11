package com.example.order.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventPublisher;
import com.example.order.domain.*;
import com.example.order.dto.request.InternalCreateOrderRequest;
import com.example.order.dto.response.InternalCreateOrderResponse;
import com.example.order.exception.OrderErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderCommandServiceTest {

    @InjectMocks
    private OrderCommandService orderCommandService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Nested
    @DisplayName("createOrder")
    class CreateOrder {

        @Test
        @DisplayName("정상 주문 생성")
        void createOrderSuccess() throws Exception {
            // given
            InternalCreateOrderRequest request = createRequest(1L);
            given(orderRepository.existsById(1L)).willReturn(false);
            given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            InternalCreateOrderResponse response = orderCommandService.createOrder(request);

            // then
            assertThat(response.getOrderId()).isEqualTo(1L);
            assertThat(response.getStatus()).isEqualTo("PAYMENT_PENDING");
            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("중복 orderId로 주문 생성 시 409 에러")
        void createOrderDuplicate() throws Exception {
            // given
            InternalCreateOrderRequest request = createRequest(1L);
            given(orderRepository.existsById(1L)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> orderCommandService.createOrder(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getErrorCode()).isEqualTo(OrderErrorCode.ORDER_ALREADY_EXISTS);
                    });
        }
    }

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

        @Test
        @DisplayName("PAYMENT_PENDING 상태에서 취소 성공")
        void cancelOrderSuccess() {
            // given
            Order order = Order.create(1L, 100L, 50000L, null, null, null, null, null);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            // when
            orderCommandService.cancelOrder(1L, 100L);

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("본인이 아닌 주문 취소 시 ORDER_NOT_FOUND")
        void cancelOrderNotOwner() {
            // given
            Order order = Order.create(1L, 100L, 50000L, null, null, null, null, null);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            // when & then
            assertThatThrownBy(() -> orderCommandService.cancelOrder(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getErrorCode()).isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
                    });
        }
    }

    @Nested
    @DisplayName("requestRefund")
    class RequestRefund {

        @Test
        @DisplayName("PAID 상태에서 환불 요청 성공")
        void requestRefundFromPaid() {
            // given
            Order order = Order.create(1L, 100L, 50000L, null, null, null, null, null);
            order.markAsPaid();
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            // when
            orderCommandService.requestRefund(1L, 100L);

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);
        }

        @Test
        @DisplayName("PAYMENT_PENDING 상태에서 환불 요청 시 ORDER_NOT_REFUNDABLE")
        void requestRefundFromPaymentPending() {
            // given
            Order order = Order.create(1L, 100L, 50000L, null, null, null, null, null);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            // when & then
            assertThatThrownBy(() -> orderCommandService.requestRefund(1L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getErrorCode()).isEqualTo(OrderErrorCode.ORDER_NOT_REFUNDABLE);
                    });
        }
    }

    private InternalCreateOrderRequest createRequest(Long orderId) throws Exception {
        InternalCreateOrderRequest request = new InternalCreateOrderRequest();
        setField(request, "orderId", orderId);
        setField(request, "userId", 100L);
        setField(request, "totalAmount", 50000L);
        setField(request, "expiresAt", LocalDateTime.now().plusMinutes(30));

        InternalCreateOrderRequest.LineItem lineItem = new InternalCreateOrderRequest.LineItem();
        setField(lineItem, "channelType", ChannelType.NORMAL);
        setField(lineItem, "itemId", 10L);
        setField(lineItem, "storeId", 1L);
        setField(lineItem, "quantity", 2);
        setField(lineItem, "finalUnitPrice", 25000L);
        setField(lineItem, "lineAmount", 50000L);

        setField(request, "lineItems", List.of(lineItem));

        return request;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
