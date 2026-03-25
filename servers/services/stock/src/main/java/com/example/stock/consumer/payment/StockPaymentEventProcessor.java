package com.example.stock.consumer.payment;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
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
    private final Map<String, EventSpec<PaymentEventMessage>> eventSpecs;

    public StockPaymentEventProcessor(
            StockCommandService stockCommandService,
            IdempotentConsumerService idempotentConsumerService
    ) {
        super(idempotentConsumerService);
        this.stockCommandService = stockCommandService;
        this.eventSpecs = Map.of(
                "PAYMENT_COMPLETED", EventSpec.of(PaymentEventMessage.class, this::hasOrderOrReservation, this::handlePaymentCompleted),
                "PAYMENT_CANCELLED", EventSpec.of(PaymentEventMessage.class, this::hasOrderOrReservation, this::handlePaymentCancelled),
                "PAYMENT_TIMED_OUT", EventSpec.of(PaymentEventMessage.class, this::hasOrderOrReservation, this::handlePaymentTimedOut),
                "PAYMENT_REFUNDED", EventSpec.of(PaymentEventMessage.class, this::hasOrderOrReservation, this::handlePaymentRefunded)
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected <T extends EventEnvelope> void onInvalidPayload(T event, String message, String eventId, String eventType) {
        log.error("[PaymentConsumer] orderId/reservationId가 모두 null입니다. message={}", message);
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
    protected Map<String, EventSpec<PaymentEventMessage>> eventSpecs() {
        return eventSpecs;
    }

    private void handlePaymentRefunded(PaymentEventMessage event) {
        if (event.getOrderId() != null) {
            log.info("결제 환불 -> order 예약 취소 처리: orderId={}", event.getOrderId());
            stockCommandService.cancelReservationsByOrderId(event.getOrderId());
            return;
        }
        log.info("결제 환불 -> 예약 취소 처리: reservationId={}", event.getReservationId());
        stockCommandService.cancelReservation(event.getReservationId());
    }

    private boolean hasOrderOrReservation(PaymentEventMessage event) {
        return event.getOrderId() != null || event.getReservationId() != null;
    }

    private void handlePaymentCompleted(PaymentEventMessage event) {
        if (event.getOrderId() != null) {
            log.info("결제 완료 -> order 예약 확정 처리: orderId={}", event.getOrderId());
            stockCommandService.confirmReservationsByOrderId(event.getOrderId());
            return;
        }
        log.info("결제 완료 -> 예약 확정 처리: reservationId={}", event.getReservationId());
        stockCommandService.confirmReservationById(event.getReservationId());
    }

    private void handlePaymentCancelled(PaymentEventMessage event) {
        if (event.getOrderId() != null) {
            log.info("결제 취소 -> order 예약 취소 처리: orderId={}", event.getOrderId());
            stockCommandService.cancelReservationsByOrderId(event.getOrderId());
            return;
        }
        log.info("결제 취소 -> 예약 취소 처리: reservationId={}", event.getReservationId());
        stockCommandService.cancelReservation(event.getReservationId());
    }

    private void handlePaymentTimedOut(PaymentEventMessage event) {
        if (event.getOrderId() != null) {
            log.info("결제 타임아웃 -> order 예약 취소 처리: orderId={}", event.getOrderId());
            stockCommandService.cancelReservationsByOrderId(event.getOrderId());
            return;
        }
        log.info("결제 타임아웃 -> 예약 취소 처리: reservationId={}", event.getReservationId());
        stockCommandService.cancelReservation(event.getReservationId());
    }
}
