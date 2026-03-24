package com.example.payment.scheduler;

import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentRepository;
import com.example.payment.domain.PaymentStatus;
import com.example.payment.service.command.PaymentEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentEventService paymentEventService;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void processExpiredPayments() {
        List<Payment> expiredPayments = paymentRepository.findByStatusAndExpiresAtBefore(
                PaymentStatus.READY, LocalDateTime.now()
        );

        if (expiredPayments.isEmpty()) {
            return;
        }

        log.info("만료된 결제 처리 시작: count={}", expiredPayments.size());

        for (Payment payment : expiredPayments) {
            try {
                paymentEventService.processTimeout(payment);
            } catch (Exception e) {
                log.error("결제 타임아웃 처리 실패: paymentId={}", payment.getId(), e);
            }
        }
    }
}
