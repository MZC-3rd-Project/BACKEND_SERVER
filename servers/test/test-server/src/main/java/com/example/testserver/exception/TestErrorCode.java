package com.example.testserver.exception;

import com.example.core.exception.DomainErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TestErrorCode implements DomainErrorCode {

    TEST_ITEM_NOT_FOUND("TEST-001", "테스트 아이템을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    TEST_ITEM_ALREADY_EXISTS("TEST-002", "이미 존재하는 테스트 아이템입니다", HttpStatus.CONFLICT),
    TEST_ITEM_NAME_REQUIRED("TEST-003", "아이템 이름은 필수입니다", HttpStatus.BAD_REQUEST),
    TEST_ITEM_LIMIT_EXCEEDED("TEST-004", "아이템 생성 한도를 초과했습니다", HttpStatus.TOO_MANY_REQUESTS);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
