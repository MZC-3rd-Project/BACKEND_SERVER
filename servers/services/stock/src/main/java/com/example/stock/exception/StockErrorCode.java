package com.example.stock.exception;

import com.example.core.exception.DomainErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StockErrorCode implements DomainErrorCode {

    // ─── 재고 ────────────────────────────────
    STOCK_NOT_FOUND("STOCK-001", "재고 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INSUFFICIENT_STOCK("STOCK-002", "재고가 부족합니다.", HttpStatus.CONFLICT),
    STOCK_ALREADY_EXISTS("STOCK-003", "이미 재고가 등록되어 있습니다.", HttpStatus.CONFLICT),
    STOCK_OVERFLOW("STOCK-004", "재고 수량이 총 수량을 초과합니다.", HttpStatus.BAD_REQUEST),

    // ─── 예약 ────────────────────────────────
    RESERVATION_NOT_FOUND("STOCK-101", "예약 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    RESERVATION_ALREADY_CONFIRMED("STOCK-102", "이미 확정된 예약입니다.", HttpStatus.CONFLICT),
    RESERVATION_ALREADY_CANCELLED("STOCK-103", "이미 취소된 예약입니다.", HttpStatus.CONFLICT),
    RESERVATION_EXPIRED("STOCK-104", "만료된 예약입니다.", HttpStatus.CONFLICT),
    INVALID_RESERVATION_STATUS("STOCK-105", "유효하지 않은 예약 상태 변경입니다.", HttpStatus.BAD_REQUEST),

    // ─── 분산 락 ────────────────────────────────
    LOCK_ACQUISITION_FAILED("STOCK-201", "락 획득에 실패했습니다. 잠시 후 다시 시도해주세요.", HttpStatus.SERVICE_UNAVAILABLE),

    // ─── 권한 ────────────────────────────────
    UNAUTHORIZED_ACCESS("STOCK-901", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
