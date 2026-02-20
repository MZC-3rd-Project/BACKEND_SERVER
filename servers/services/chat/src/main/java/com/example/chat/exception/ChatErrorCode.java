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
    ROOM_READ_ONLY("CHAT-004", "읽기 전용 채팅방에서는 일반 메시지를 보낼 수 없습니다.", HttpStatus.BAD_REQUEST),
    NOTICE_PERMISSION_DENIED("CHAT-005", "공지 메시지 전송 권한이 없습니다.", HttpStatus.FORBIDDEN),
    INVALID_MESSAGE_TYPE("CHAT-006", "유효하지 않은 메시지 타입입니다.", HttpStatus.BAD_REQUEST),
    INVALID_MESSAGE_CONTENT("CHAT-007", "유효하지 않은 메시지 내용입니다.", HttpStatus.BAD_REQUEST),
    PLATFORM_ADMIN_CHAT_NOT_ALLOWED("CHAT-008", "플랫폼 어드민은 일반 채팅을 전송할 수 없습니다.", HttpStatus.FORBIDDEN),
    INVALID_INQUIRY_REQUEST("CHAT-010", "문의방 생성 요청이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),

    PRODUCT_SERVICE_ERROR("CHAT-201", "상품 서비스 호출에 실패했습니다.", HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
