package com.example.storequery.exception;

import com.example.core.exception.DomainErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StoreQueryErrorCode implements DomainErrorCode {

    STORE_READ_MODEL_NOT_FOUND("STOREQ-001", "스토어 조회 모델을 찾을 수 없습니다", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
