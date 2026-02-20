package com.example.search.exception;

import com.example.core.exception.DomainErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SearchErrorCode implements DomainErrorCode {

    SEARCH_TEMPORARILY_UNAVAILABLE("SEARCH-001", "검색 시스템이 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.", HttpStatus.SERVICE_UNAVAILABLE),
    INVALID_SEARCH_PARAMETER("SEARCH-002", "검색 파라미터가 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    SEARCH_INDEX_TEMPLATE_ERROR("SEARCH-003", "검색 인덱스 템플릿 구성이 올바르지 않습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INDEX_MANAGEMENT_FAILED("SEARCH-004", "검색 인덱스 관리 작업에 실패했습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    UNAUTHORIZED_INTERNAL_API("SEARCH-901", "내부 API 접근 권한이 없습니다.", HttpStatus.UNAUTHORIZED);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
