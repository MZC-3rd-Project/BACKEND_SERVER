package com.example.payment.exception;

import com.example.core.exception.DomainErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements DomainErrorCode {

    PAYMENT_NOT_FOUND("PAYMENT-001", "결제 정보를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    INVALID_STATUS_TRANSITION("PAYMENT-002", "유효하지 않은 결제 상태 전이입니다", HttpStatus.BAD_REQUEST),
    AMOUNT_MISMATCH("PAYMENT-003", "결제 금액이 일치하지 않습니다", HttpStatus.BAD_REQUEST),
    PAYMENT_EXPIRED("PAYMENT-004", "결제 유효기간이 만료되었습니다", HttpStatus.BAD_REQUEST),
    PAYMENT_NOT_READY("PAYMENT-005", "결제 대기 상태가 아닙니다", HttpStatus.BAD_REQUEST),
    PAYMENT_ALREADY_EXISTS("PAYMENT-006", "해당 주문에 대한 결제가 이미 존재합니다", HttpStatus.CONFLICT),
    TOSS_CONFIRM_FAILED("PAYMENT-007", "토스페이먼츠 결제 승인에 실패했습니다", HttpStatus.BAD_GATEWAY),
    TOSS_CANCEL_FAILED("PAYMENT-008", "토스페이먼츠 결제 취소에 실패했습니다", HttpStatus.BAD_GATEWAY),
    UNAUTHORIZED_PAYMENT_ACCESS("PAYMENT-009", "해당 결제에 대한 접근 권한이 없습니다", HttpStatus.FORBIDDEN);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
