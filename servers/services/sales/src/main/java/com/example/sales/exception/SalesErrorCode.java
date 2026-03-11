package com.example.sales.exception;

import com.example.core.exception.DomainErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SalesErrorCode implements DomainErrorCode {

    // ─── Checkout Session ────────────────────────
    INVALID_STATUS_TRANSITION("SALES-003", "유효하지 않은 상태 전이입니다", HttpStatus.BAD_REQUEST),
    INVALID_REQUEST("SALES-005", "잘못된 요청입니다", HttpStatus.BAD_REQUEST),
    CHECKOUT_SESSION_NOT_FOUND("SALES-006", "checkout session 정보를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    CHECKOUT_SESSION_FORBIDDEN("SALES-007", "해당 checkout session에 접근할 수 없습니다", HttpStatus.FORBIDDEN),
    CHECKOUT_SESSION_EXPIRED("SALES-008", "checkout session이 만료되었습니다", HttpStatus.CONFLICT),
    CHECKOUT_IDEMPOTENCY_CONFLICT("SALES-009", "이미 처리된 checkout 요청입니다. 새 idempotencyKey를 사용해주세요.", HttpStatus.CONFLICT),
    CHECKOUT_SESSION_CANCELLED("SALES-010", "checkout session이 취소되었습니다", HttpStatus.CONFLICT),
    CHECKOUT_SUBMIT_IN_PROGRESS("SALES-011", "checkout submit이 이미 처리 중입니다", HttpStatus.CONFLICT),
    CHECKOUT_SUBMIT_INVALID("SALES-012", "checkout submit 요청이 유효하지 않습니다", HttpStatus.BAD_REQUEST),
    CHECKOUT_SUBMIT_CONFLICT("SALES-013", "주문 생성 요청이 충돌했습니다", HttpStatus.CONFLICT),

    // ─── External Service ────────────────────────
    STOCK_SERVICE_ERROR("SALES-201", "재고 서비스 호출 중 오류가 발생했습니다", HttpStatus.SERVICE_UNAVAILABLE),
    PRODUCT_SERVICE_ERROR("SALES-202", "상품 서비스 호출 중 오류가 발생했습니다", HttpStatus.SERVICE_UNAVAILABLE),
    STOCK_INSUFFICIENT("SALES-203", "재고가 부족합니다", HttpStatus.CONFLICT),
    ORDER_SERVICE_ERROR("SALES-204", "주문 서비스 호출 중 오류가 발생했습니다", HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
