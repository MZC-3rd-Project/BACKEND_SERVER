package com.example.payment.service.command;

import com.example.config.lock.DistributedLock;
import com.example.core.exception.BusinessException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.payment.client.TossPaymentsClient;
import com.example.payment.client.dto.TossConfirmRequest;
import com.example.payment.client.dto.TossConfirmResponse;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentRepository;
import com.example.payment.domain.PaymentStatus;
import com.example.payment.dto.request.PaymentConfirmRequest;
import com.example.payment.dto.response.PaymentConfirmResponse;
import com.example.payment.event.PaymentCompletedEvent;
import com.example.payment.exception.PaymentErrorCode;
import com.example.payment.exception.TossPaymentsApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentCommandService {

    private final PaymentRepository paymentRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final PaymentFailureHandler paymentFailureHandler;

    @DistributedLock(key = "'payment:confirm:' + #request.orderId()", waitTime = 5, leaseTime = 15)
    public PaymentConfirmResponse confirmPayment(PaymentConfirmRequest request, Long userId) {
        Payment payment = paymentRepository.findByOrderId(request.orderId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        if (!payment.getUserId().equals(userId)) {
            throw new BusinessException(PaymentErrorCode.UNAUTHORIZED_PAYMENT_ACCESS);
        }

        if (payment.getStatus() != PaymentStatus.READY) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_READY);
        }

        if (!payment.getAmount().equals(request.amount())) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }

        if (payment.getExpiresAt() != null && payment.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_EXPIRED);
        }

        try {
            TossConfirmResponse tossResponse = tossPaymentsClient.confirm(
                    new TossConfirmRequest(request.paymentKey(), String.valueOf(request.orderId()), request.amount())
            );

            String tossResponseJson = serializeResponse(tossResponse);

            LocalDateTime paidAt = tossResponse.approvedAt() != null
                    ? LocalDateTime.parse(tossResponse.approvedAt().substring(0, 19))
                    : LocalDateTime.now();

            payment.markDone(
                    tossResponse.paymentKey(),
                    tossResponse.orderId(),
                    tossResponse.method(),
                    paidAt,
                    tossResponseJson
            );

            eventPublisher.publish(
                    new PaymentCompletedEvent(
                            payment.getId(),
                            payment.getOrderId(),
                            payment.getUserId(),
                            payment.getAmount(),
                            paidAt
                    ),
                    EventMetadata.of("Payment", String.valueOf(payment.getId()))
            );

            log.info("결제 승인 완료: paymentId={}, orderId={}", payment.getId(), payment.getOrderId());
            return PaymentConfirmResponse.from(payment);

        } catch (TossPaymentsApiException e) {
            paymentFailureHandler.handleConfirmFailure(
                    payment.getId(), request.paymentKey(), e.getResponseBody()
            );
            log.error("결제 승인 실패: paymentId={}, orderId={}, error={}",
                    payment.getId(), payment.getOrderId(), e.getResponseBody());
            throw new BusinessException(PaymentErrorCode.TOSS_CONFIRM_FAILED);
        }
    }

    private String serializeResponse(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.warn("토스 응답 직렬화 실패", e);
            return null;
        }
    }
}
