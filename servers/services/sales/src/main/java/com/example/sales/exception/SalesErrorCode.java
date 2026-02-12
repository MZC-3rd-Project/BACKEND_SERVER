package com.example.sales.exception;

import com.example.core.exception.DomainErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SalesErrorCode implements DomainErrorCode {

    // ─── Purchase ────────────────────────────────
    PURCHASE_NOT_FOUND("SALES-001", "구매 정보를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    PURCHASE_ALREADY_EXISTS("SALES-002", "이미 존재하는 구매입니다", HttpStatus.CONFLICT),
    INVALID_STATUS_TRANSITION("SALES-003", "유효하지 않은 상태 전이입니다", HttpStatus.BAD_REQUEST),
    PURCHASE_NOT_CANCELLABLE("SALES-004", "취소할 수 없는 구매입니다", HttpStatus.BAD_REQUEST),

    // ─── External Service ────────────────────────
    STOCK_SERVICE_ERROR("SALES-201", "재고 서비스 호출 중 오류가 발생했습니다", HttpStatus.SERVICE_UNAVAILABLE),
    PRODUCT_SERVICE_ERROR("SALES-202", "상품 서비스 호출 중 오류가 발생했습니다", HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
