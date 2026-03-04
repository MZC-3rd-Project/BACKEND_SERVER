package com.example.order.exception;

import com.example.core.exception.DomainErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements DomainErrorCode {

    ORDER_NOT_FOUND("ORDER-001", "주문을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    INVALID_STATUS_TRANSITION("ORDER-002", "유효하지 않은 주문 상태 전이입니다", HttpStatus.BAD_REQUEST),
    ORDER_ALREADY_EXISTS("ORDER-003", "이미 존재하는 주문입니다", HttpStatus.CONFLICT),
    ORDER_NOT_CANCELLABLE("ORDER-004", "취소할 수 없는 주문입니다", HttpStatus.BAD_REQUEST),
    ORDER_NOT_REFUNDABLE("ORDER-005", "환불할 수 없는 주문입니다", HttpStatus.BAD_REQUEST),
    ORDER_ITEM_EMPTY("ORDER-006", "주문 항목이 비어있습니다", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
