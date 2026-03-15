package com.example.cart.exception;

import com.example.core.exception.DomainErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CartErrorCode implements DomainErrorCode {

    CART_LINE_NOT_FOUND("CART-001", "장바구니 항목을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    EMPTY_SELECTED_ITEMS("CART-002", "선택된 장바구니 항목이 없습니다", HttpStatus.BAD_REQUEST),
    INVALID_CART_LINE("CART-003", "유효하지 않은 장바구니 항목입니다", HttpStatus.BAD_REQUEST),
    CHECKOUT_REJECTED("CART-004", "체크아웃 예약이 거절되었습니다", HttpStatus.CONFLICT),
    CHECKOUT_FAILED("CART-005", "체크아웃 예약 호출에 실패했습니다", HttpStatus.BAD_GATEWAY);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
