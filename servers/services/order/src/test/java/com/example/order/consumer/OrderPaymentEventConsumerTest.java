package com.example.order.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.util.JsonUtils;
import com.example.order.domain.Order;
import com.example.order.domain.OrderStatus;
import com.example.order.service.command.OrderCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderPaymentEventConsumerTest {

    @InjectMocks
    private OrderPaymentEventConsumer consumer;

    @Mock
    private OrderCommandService orderCommandService;

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Test
    @DisplayName("PAYMENT_COMPLETED_EVENT → Order PAID 전이")
    void handlePaymentCompleted() {
        // given
        String eventId = UUID.randomUUID().toString();
        String message = createEventMessage(eventId, "PAYMENT_COMPLETED_EVENT", 1L);

        doAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(2);
            action.get();
            return null;
        }).when(idempotentConsumerService).executeIdempotent(eq(eventId), eq("PAYMENT_EVENT"), any());

        // when
        consumer.consume(message);

        // then
        verify(orderCommandService).markAsPaid(1L);
    }

    @Test
    @DisplayName("PAYMENT_FAILED_EVENT → Order CANCELLED 전이")
    void handlePaymentFailed() {
        // given
        String eventId = UUID.randomUUID().toString();
        String message = createEventMessage(eventId, "PAYMENT_FAILED_EVENT", 1L);

        doAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(2);
            action.get();
            return null;
        }).when(idempotentConsumerService).executeIdempotent(eq(eventId), eq("PAYMENT_EVENT"), any());

        // when
        consumer.consume(message);

        // then
        verify(orderCommandService).markAsCancelled(1L);
    }

    @Test
    @DisplayName("PAYMENT_TIMED_OUT_EVENT → Order CANCELLED 전이")
    void handlePaymentTimedOut() {
        // given
        String eventId = UUID.randomUUID().toString();
        String message = createEventMessage(eventId, "PAYMENT_TIMED_OUT_EVENT", 1L);

        doAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(2);
            action.get();
            return null;
        }).when(idempotentConsumerService).executeIdempotent(eq(eventId), eq("PAYMENT_EVENT"), any());

        // when
        consumer.consume(message);

        // then
        verify(orderCommandService).markAsCancelled(1L);
    }

    @Test
    @DisplayName("PAYMENT_REFUNDED_EVENT → Order REFUNDED 전이")
    void handlePaymentRefunded() {
        // given
        String eventId = UUID.randomUUID().toString();
        String message = createEventMessage(eventId, "PAYMENT_REFUNDED_EVENT", 1L);

        doAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(2);
            action.get();
            return null;
        }).when(idempotentConsumerService).executeIdempotent(eq(eventId), eq("PAYMENT_EVENT"), any());

        // when
        consumer.consume(message);

        // then
        verify(orderCommandService).markAsRefunded(1L);
    }

    @Test
    @DisplayName("중복 이벤트 → 멱등성 검증 (두 번째는 스킵)")
    void idempotencyCheck() {
        // given
        String eventId = UUID.randomUUID().toString();
        String message = createEventMessage(eventId, "PAYMENT_COMPLETED_EVENT", 1L);

        // 첫 번째 호출: 정상 실행
        doAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(2);
            action.get();
            return null;
        }).when(idempotentConsumerService).executeIdempotent(eq(eventId), eq("PAYMENT_EVENT"), any());

        consumer.consume(message);

        // 두 번째 호출: IdempotentConsumerService가 스킵 처리 (action 실행 안 함)
        reset(orderCommandService);
        doAnswer(invocation -> {
            // action을 실행하지 않음 = 이미 처리된 이벤트
            return null;
        }).when(idempotentConsumerService).executeIdempotent(eq(eventId), eq("PAYMENT_EVENT"), any());

        consumer.consume(message);

        // then - 두 번째에서는 markAsPaid가 호출되지 않음
        verify(orderCommandService, never()).markAsPaid(anyLong());
    }

    private String createEventMessage(String eventId, String eventType, Long orderId) {
        return """
                {
                    "eventId": "%s",
                    "eventType": "%s",
                    "orderId": %d,
                    "userId": 100,
                    "amount": 50000
                }
                """.formatted(eventId, eventType, orderId);
    }
}
