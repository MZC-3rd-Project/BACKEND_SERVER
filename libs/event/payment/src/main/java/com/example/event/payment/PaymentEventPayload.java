package com.example.event.payment;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 결제 서비스가 발행하는 payment-events 토픽 메시지의 공통 명세.
 *
 * <p>각 이벤트 타입별 유효 필드:
 * <ul>
 *   <li>{@code PAYMENT_COMPLETED}  — paymentId, orderId, userId, amount, paidAt</li>
 *   <li>{@code PAYMENT_FAILED}     — paymentId, orderId, userId, amount, failReason</li>
 *   <li>{@code PAYMENT_CANCELLED}  — paymentId, orderId, userId, amount</li>
 *   <li>{@code PAYMENT_TIMED_OUT}  — paymentId, orderId, userId, amount</li>
 *   <li>{@code PAYMENT_REFUNDED}   — paymentId, orderId, userId, amount</li>
 * </ul>
 *
 * <p>이벤트 타입 상수는 {@link PaymentEventType}을 사용한다.
 */
@Getter
@NoArgsConstructor
public class PaymentEventPayload implements EventEnvelope {

    /** 이벤트 고유 ID */
    private String eventId;

    /** 이벤트 타입 — {@link PaymentEventType#value()} 와 일치 */
    private String eventType;

    /** 결제 ID */
    private Long paymentId;

    /** 주문 ID */
    private Long orderId;

    /** 사용자 ID */
    private Long userId;

    /** 결제 금액 */
    private Long amount;

    /** 결제 승인 시각 — PAYMENT_COMPLETED 시에만 값이 있음 */
    private LocalDateTime paidAt;

    /** 실패 사유 — PAYMENT_FAILED 시에만 값이 있음 */
    private String failReason;
}
