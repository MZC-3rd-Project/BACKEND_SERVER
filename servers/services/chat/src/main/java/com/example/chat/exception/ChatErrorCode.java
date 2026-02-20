package com.example.chat.exception;

import com.example.core.exception.DomainErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements DomainErrorCode {

    ROOM_NOT_FOUND("CHAT-001", "채팅방을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    FORBIDDEN_ROOM_ACCESS("CHAT-003", "채팅방 접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    INVALID_INQUIRY_REQUEST("CHAT-010", "문의방 생성 요청이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),

    PRODUCT_SERVICE_ERROR("CHAT-201", "상품 서비스 호출에 실패했습니다.", HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
