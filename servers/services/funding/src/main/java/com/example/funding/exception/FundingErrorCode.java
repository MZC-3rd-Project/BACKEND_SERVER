package com.example.funding.exception;

import com.example.core.exception.DomainErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FundingErrorCode implements DomainErrorCode {

    // ─── 캠페인 ────────────────────────────────
    CAMPAIGN_NOT_FOUND("FUNDING-001", "펀딩 캠페인을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    CAMPAIGN_NOT_ACTIVE("FUNDING-002", "진행 중인 펀딩이 아닙니다.", HttpStatus.BAD_REQUEST),
    CAMPAIGN_ALREADY_EXISTS("FUNDING-003", "해당 상품에 이미 펀딩 캠페인이 존재합니다.", HttpStatus.CONFLICT),
    CAMPAIGN_NOT_EDITABLE("FUNDING-004", "수정할 수 없는 상태의 캠페인입니다.", HttpStatus.BAD_REQUEST),
    INVALID_CAMPAIGN_PERIOD("FUNDING-005", "유효하지 않은 펀딩 기간입니다.", HttpStatus.BAD_REQUEST),
    INVALID_STATUS_TRANSITION("FUNDING-006", "유효하지 않은 상태 전이입니다.", HttpStatus.BAD_REQUEST),

    // ─── 참여 ────────────────────────────────
    PARTICIPATION_NOT_FOUND("FUNDING-101", "참여 내역을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    GOAL_QUANTITY_EXCEEDED("FUNDING-102", "펀딩 목표 수량을 초과했습니다.", HttpStatus.CONFLICT),
    BELOW_MIN_AMOUNT("FUNDING-103", "최소 참여 금액 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
    ALREADY_PARTICIPATED("FUNDING-104", "이미 참여한 펀딩입니다.", HttpStatus.CONFLICT),
    INVALID_PARTICIPATION_STATUS("FUNDING-105", "유효하지 않은 참여 상태 변경입니다.", HttpStatus.BAD_REQUEST),

    // ─── 외부 서비스 ────────────────────────────────
    PRODUCT_SERVICE_ERROR("FUNDING-201", "상품 서비스 호출에 실패했습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    STOCK_SERVICE_ERROR("FUNDING-202", "재고 서비스 호출에 실패했습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    PAYMENT_SERVICE_ERROR("FUNDING-203", "결제 서비스 호출에 실패했습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    STOCK_INSUFFICIENT("FUNDING-204", "재고가 부족합니다.", HttpStatus.CONFLICT),

    // ─── 권한 ────────────────────────────────
    UNAUTHORIZED_ACCESS("FUNDING-901", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
