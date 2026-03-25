package com.example.payment.service.command;

import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentRepository;
import com.example.payment.event.PaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFailureHandler {

    private final PaymentRepository paymentRepository;
    private final EventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleConfirmFailure(Long paymentId, String paymentKey, String failReason) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            log.warn("결제 실패 처리 대상 없음: paymentId={}", paymentId);
            return;
        }

        payment.markFailed(paymentKey, failReason, failReason);

        eventPublisher.publish(
                new PaymentFailedEvent(
                        payment.getId(),
                        payment.getOrderId(),
                        payment.getUserId(),
                        payment.getAmount(),
                        failReason
                ),
                EventMetadata.of("Payment", String.valueOf(payment.getId()))
        );

        log.info("결제 실패 처리 완료 (REQUIRES_NEW): paymentId={}, orderId={}",
                payment.getId(), payment.getOrderId());
    }
}
