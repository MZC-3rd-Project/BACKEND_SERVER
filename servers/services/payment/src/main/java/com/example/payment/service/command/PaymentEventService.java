package com.example.payment.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.payment.client.TossPaymentsClient;
import com.example.payment.client.dto.TossCancelRequest;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentRepository;
import com.example.payment.domain.PaymentStatus;
import com.example.payment.event.PaymentCancelledEvent;
import com.example.payment.event.PaymentRefundedEvent;
import com.example.payment.event.PaymentTimedOutEvent;
import com.example.payment.exception.PaymentErrorCode;
import com.example.payment.exception.TossPaymentsApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentEventService {

    private final PaymentRepository paymentRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final EventPublisher eventPublisher;

    public void processRefund(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getStatus() != PaymentStatus.DONE) {
            log.warn("환불 불가 상태: paymentId={}, status={}", payment.getId(), payment.getStatus());
            return;
        }

        try {
            tossPaymentsClient.cancel(
                    payment.getPaymentKey(),
                    new TossCancelRequest("주문 환불 요청", payment.getAmount()),
                    payment.getOrderId() + ":refund"
            );

            payment.markCancelled("주문 환불 요청");

            eventPublisher.publish(
                    new PaymentRefundedEvent(
                            payment.getId(),
                            payment.getOrderId(),
                            payment.getUserId(),
                            payment.getAmount()
                    ),
                    EventMetadata.of("Payment", String.valueOf(payment.getId()))
            );

            log.info("환불 처리 완료: paymentId={}, orderId={}", payment.getId(), orderId);

        } catch (TossPaymentsApiException e) {
            log.error("토스 환불 실패: paymentId={}, orderId={}, error={}",
                    payment.getId(), orderId, e.getResponseBody());
            throw new BusinessException(PaymentErrorCode.TOSS_CANCEL_FAILED);
        }
    }

    public void processCancellation(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getStatus() != PaymentStatus.READY) {
            log.warn("취소 불가 상태: paymentId={}, status={}", payment.getId(), payment.getStatus());
            return;
        }

        payment.markCancelled("주문 취소");

        eventPublisher.publish(
                new PaymentCancelledEvent(
                        payment.getId(),
                        payment.getOrderId(),
                        payment.getUserId(),
                        payment.getAmount()
                ),
                EventMetadata.of("Payment", String.valueOf(payment.getId()))
        );

        log.info("결제 취소 처리 완료: paymentId={}, orderId={}", payment.getId(), orderId);
    }

    public void processTimeout(Payment payment) {
        if (payment.getStatus() != PaymentStatus.READY) {
            log.warn("타임아웃 처리 불가 상태: paymentId={}, status={}", payment.getId(), payment.getStatus());
            return;
        }

        payment.markExpired();

        eventPublisher.publish(
                new PaymentTimedOutEvent(
                        payment.getId(),
                        payment.getOrderId(),
                        payment.getUserId(),
                        payment.getAmount()
                ),
                EventMetadata.of("Payment", String.valueOf(payment.getId()))
        );

        log.info("결제 타임아웃 처리: paymentId={}, orderId={}", payment.getId(), payment.getOrderId());
    }
}
