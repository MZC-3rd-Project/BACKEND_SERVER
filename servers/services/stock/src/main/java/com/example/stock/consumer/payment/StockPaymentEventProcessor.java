package com.example.stock.consumer.payment;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.event.payment.PaymentEventPayload;
import com.example.event.payment.PaymentEventType;
import com.example.stock.service.command.StockCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = StockPaymentEventProcessor.CONSUMER_NAME)
public class StockPaymentEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "stock-payment-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "PAYMENT_EVENT";
    private final StockCommandService stockCommandService;
    private final Map<String, EventSpec<PaymentEventPayload>> eventSpecs;

    public StockPaymentEventProcessor(
            StockCommandService stockCommandService,
            IdempotentConsumerService idempotentConsumerService
    ) {
        super(idempotentConsumerService);
        this.stockCommandService = stockCommandService;
        this.eventSpecs = Map.ofEntries(
                Map.entry(PaymentEventType.PAYMENT_COMPLETED.value(),
                        EventSpec.of(PaymentEventPayload.class, this::hasOrderId, this::handlePaymentCompleted)),
                Map.entry(PaymentEventType.PAYMENT_FAILED.value(),
                        EventSpec.of(PaymentEventPayload.class, this::hasOrderId, this::handlePaymentFailed)),
                Map.entry(PaymentEventType.PAYMENT_CANCELLED.value(),
                        EventSpec.of(PaymentEventPayload.class, this::hasOrderId, this::handlePaymentCancelled)),
                Map.entry(PaymentEventType.PAYMENT_TIMED_OUT.value(),
                        EventSpec.of(PaymentEventPayload.class, this::hasOrderId, this::handlePaymentTimedOut)),
                Map.entry(PaymentEventType.PAYMENT_REFUNDED.value(),
                        EventSpec.of(PaymentEventPayload.class, this::hasOrderId, this::handlePaymentRefunded))
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected <T extends EventEnvelope> void onInvalidPayload(T event, String message, String eventId, String eventType) {
        log.error("[PaymentConsumer] orderId가 null입니다. eventId={}, eventType={}, message={}", eventId, eventType, message);
    }

    @Override
    protected void onInvalidEnvelope(String eventId, String eventType, String message) {
        log.error("[PaymentConsumer] eventId 또는 eventType이 null입니다. message={}", message);
    }

    @Override
    protected <T extends EventEnvelope> void onProcessingException(
            T event,
            String message,
            String eventId,
            String eventType,
            Exception exception
    ) {
        log.error("[PaymentConsumer] 이벤트 처리 실패: {}", message, exception);
        throw propagate(exception);
    }

    @Override
    protected Map<String, EventSpec<PaymentEventPayload>> eventSpecs() {
        return eventSpecs;
    }

    private boolean hasOrderId(PaymentEventPayload event) {
        return event.getOrderId() != null;
    }

    private void handlePaymentCompleted(PaymentEventPayload event) {
        log.info("결제 완료 -> 재고 예약 확정: orderId={}", event.getOrderId());
        stockCommandService.confirmReservationsByOrderId(event.getOrderId());
    }

    private void handlePaymentFailed(PaymentEventPayload event) {
        log.info("결제 실패 -> 재고 예약 취소: orderId={}", event.getOrderId());
        stockCommandService.cancelReservationsByOrderId(event.getOrderId());
    }

    private void handlePaymentCancelled(PaymentEventPayload event) {
        log.info("결제 취소 -> 재고 예약 취소: orderId={}", event.getOrderId());
        stockCommandService.cancelReservationsByOrderId(event.getOrderId());
    }

    private void handlePaymentTimedOut(PaymentEventPayload event) {
        log.info("결제 타임아웃 -> 재고 예약 취소: orderId={}", event.getOrderId());
        stockCommandService.cancelReservationsByOrderId(event.getOrderId());
    }

    private void handlePaymentRefunded(PaymentEventPayload event) {
        log.info("결제 환불 -> 재고 예약 취소: orderId={}", event.getOrderId());
        stockCommandService.cancelReservationsByOrderId(event.getOrderId());
    }
}
