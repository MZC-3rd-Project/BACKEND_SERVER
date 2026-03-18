package com.example.orderquery.exception;

import com.example.core.exception.DomainErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderQueryErrorCode implements DomainErrorCode {

    ORDER_NOT_FOUND("ORDER_QUERY_001", "주문을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
