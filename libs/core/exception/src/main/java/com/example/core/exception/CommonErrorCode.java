package com.example.core.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    // Authentication / Authorization
    UNAUTHORIZED("AUTH-001", "인증이 필요합니다", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("AUTH-002", "접근 권한이 없습니다", HttpStatus.FORBIDDEN),
    INVALID_TOKEN("AUTH-003", "유효하지 않은 토큰입니다", HttpStatus.UNAUTHORIZED),
    EXPIRED_TOKEN("AUTH-004", "만료된 토큰입니다", HttpStatus.UNAUTHORIZED),

    // Validation
    INVALID_REQUEST("VALID-001", "잘못된 요청입니다", HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR("VALID-002", "입력값 검증에 실패했습니다", HttpStatus.BAD_REQUEST),
    MISSING_PARAMETER("VALID-003", "필수 파라미터가 누락되었습니다", HttpStatus.BAD_REQUEST),
    METHOD_NOT_ALLOWED("VALID-004", "지원하지 않는 HTTP 메서드입니다", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE("VALID-005", "지원하지 않는 미디어 타입입니다", HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    // Resources
    RESOURCE_NOT_FOUND("RSRC-001", "요청한 리소스를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    RESOURCE_ALREADY_EXISTS("RSRC-002", "이미 존재하는 리소스입니다", HttpStatus.CONFLICT),

    // Server
    INTERNAL_ERROR("SYS-001", "내부 서버 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE("SYS-002", "서비스를 일시적으로 사용할 수 없습니다", HttpStatus.SERVICE_UNAVAILABLE),
    EXTERNAL_API_ERROR("SYS-003", "외부 API 호출에 실패했습니다", HttpStatus.BAD_GATEWAY);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
