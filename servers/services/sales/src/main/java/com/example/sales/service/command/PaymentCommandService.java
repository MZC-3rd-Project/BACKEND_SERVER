package com.example.sales.service.command;

import com.example.sales.dto.payment.request.PaymentConfirmRequest;
import com.example.sales.dto.payment.response.PaymentConfirmResponse;
import com.example.sales.entity.CheckoutSession;
import com.example.sales.entity.CheckoutSessionStatus;
import com.example.sales.exception.SalesErrorCode;
import com.example.core.exception.BusinessException;
import com.example.sales.repository.CheckoutSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCommandService {

    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CheckoutSessionRepository checkoutSessionRepository;

    public PaymentConfirmResponse confirm(PaymentConfirmRequest request, Long userId) {
        CheckoutSession session = checkoutSessionRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new BusinessException(SalesErrorCode.CHECKOUT_SESSION_NOT_FOUND));

        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(SalesErrorCode.CHECKOUT_SESSION_FORBIDDEN);
        }

        if (session.getStatus() != CheckoutSessionStatus.ORDER_CREATED) {
            throw new BusinessException(SalesErrorCode.CHECKOUT_SESSION_NOT_FOUND);
        }

        String eventId = UUID.randomUUID().toString();
        long paymentId = System.currentTimeMillis();
        LocalDateTime paidAt = LocalDateTime.now();

        Map<String, Object> paymentEvent = Map.of(
                "eventId", eventId,
                "eventType", "PAYMENT_COMPLETED",
                "paymentId", paymentId,
                "orderId", request.getOrderId(),
                "userId", userId,
                "amount", request.getAmount(),
                "paidAt", paidAt.toString()
        );

        kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, String.valueOf(request.getOrderId()), paymentEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish payment event: orderId={}, error={}", request.getOrderId(), ex.getMessage());
                    } else {
                        log.info("Payment event published: orderId={}, paymentId={}", request.getOrderId(), paymentId);
                    }
                });

        return PaymentConfirmResponse.builder()
                .orderId(request.getOrderId())
                .paymentId(paymentId)
                .amount(request.getAmount())
                .status("PAYMENT_COMPLETED")
                .paidAt(paidAt)
                .build();
    }
}
