package com.example.hotdeal.exception;

import com.example.core.exception.DomainErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum HotDealErrorCode implements DomainErrorCode {

    // ─── 핫딜 ────────────────────────────────
    HOT_DEAL_NOT_FOUND("HOTDEAL-001", "핫딜 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    HOT_DEAL_NOT_ACTIVE("HOTDEAL-002", "현재 진행 중인 핫딜이 아닙니다.", HttpStatus.BAD_REQUEST),
    HOT_DEAL_ALREADY_EXISTS("HOTDEAL-003", "이미 등록된 핫딜입니다.", HttpStatus.CONFLICT),
    INVALID_STATUS_TRANSITION("HOTDEAL-004", "유효하지 않은 상태 전이입니다.", HttpStatus.BAD_REQUEST),
    HOT_DEAL_SOLD_OUT("HOTDEAL-005", "핫딜 수량이 모두 소진되었습니다.", HttpStatus.CONFLICT),

    // ─── 선착순 구매 ────────────────────────────
    PURCHASE_QUANTITY_EXCEEDED("HOTDEAL-101", "구매 가능 수량을 초과했습니다.", HttpStatus.BAD_REQUEST),
    PURCHASE_ALREADY_EXISTS("HOTDEAL-102", "이미 구매한 핫딜입니다.", HttpStatus.CONFLICT),
    RESERVATION_EXPIRED("HOTDEAL-103", "임시 예약이 만료되었습니다.", HttpStatus.CONFLICT),

    // ─── 대기열 ────────────────────────────────
    QUEUE_ALREADY_ENTERED("HOTDEAL-201", "이미 대기열에 등록되어 있습니다.", HttpStatus.CONFLICT),
    QUEUE_TOKEN_INVALID("HOTDEAL-202", "유효하지 않은 대기열 토큰입니다.", HttpStatus.UNAUTHORIZED),
    QUEUE_NOT_ADMITTED("HOTDEAL-203", "아직 입장이 허용되지 않았습니다.", HttpStatus.FORBIDDEN),

    // ─── 외부 서비스 ────────────────────────────
    PRODUCT_SERVICE_ERROR("HOTDEAL-301", "상품 서비스 호출 중 오류가 발생했습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    STOCK_SERVICE_ERROR("HOTDEAL-302", "재고 서비스 호출 중 오류가 발생했습니다.", HttpStatus.SERVICE_UNAVAILABLE),

    // ─── 분산 락 ────────────────────────────────
    LOCK_ACQUISITION_FAILED("HOTDEAL-401", "락 획득에 실패했습니다. 잠시 후 다시 시도해주세요.", HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
