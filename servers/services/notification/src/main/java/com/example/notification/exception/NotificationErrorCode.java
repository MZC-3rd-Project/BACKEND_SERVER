package com.example.notification.exception;

import com.example.core.exception.DomainErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements DomainErrorCode {

    // ─── Notification ────────────────────────────────
    NOTIFICATION_NOT_FOUND("NOTIFICATION-001", "알림을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    TEMPLATE_NOT_FOUND("NOTIFICATION-002", "알림 템플릿을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INVALID_NOTIFICATION_TYPE("NOTIFICATION-003", "유효하지 않은 알림 유형입니다.", HttpStatus.BAD_REQUEST),
    INVALID_CHANNEL("NOTIFICATION-004", "유효하지 않은 알림 채널입니다.", HttpStatus.BAD_REQUEST),
    INVALID_SETTING_REQUEST("NOTIFICATION-005", "알림 설정 변경 요청이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    INVALID_TIMEZONE("NOTIFICATION-006", "유효하지 않은 타임존입니다.", HttpStatus.BAD_REQUEST),
    INVALID_QUIET_HOURS("NOTIFICATION-007", "유효하지 않은 방해 금지 시간 설정입니다.", HttpStatus.BAD_REQUEST),
    FORBIDDEN_RECIPIENT("NOTIFICATION-008", "요청 사용자와 수신자가 일치하지 않습니다.", HttpStatus.FORBIDDEN),
    FORBIDDEN_ACTOR("NOTIFICATION-009", "요청 사용자가 행위자를 임의 지정할 수 없습니다.", HttpStatus.FORBIDDEN),

    // ─── Delivery ───────────────────────────────────
    SSE_DELIVERY_FAILED("NOTIFICATION-201", "SSE 알림 전송에 실패했습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    EMAIL_DELIVERY_FAILED("NOTIFICATION-202", "이메일 알림 전송에 실패했습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    SSE_CONNECTION_LIMIT_EXCEEDED("NOTIFICATION-203", "SSE 연결 허용 개수를 초과했습니다.", HttpStatus.TOO_MANY_REQUESTS),

    // ─── External Service ───────────────────────────
    USER_SERVICE_ERROR("NOTIFICATION-301", "유저 서비스 호출에 실패했습니다.", HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
