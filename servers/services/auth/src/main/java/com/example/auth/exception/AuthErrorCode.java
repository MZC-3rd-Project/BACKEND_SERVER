package com.example.auth.exception;

import com.example.core.exception.DomainErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements DomainErrorCode {

    // 회원가입
    EMAIL_ALREADY_EXISTS("AUTH-001", "이미 사용 중인 이메일입니다", HttpStatus.CONFLICT),
    SIGNUP_KEYCLOAK_FAILED("AUTH-002", "Keycloak 사용자 생성에 실패했습니다", HttpStatus.BAD_GATEWAY),
    SIGNUP_PROFILE_FAILED("AUTH-003", "프로필 생성에 실패했습니다", HttpStatus.BAD_GATEWAY),

    // 인증
    USER_NOT_FOUND("AUTH-010", "사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    INVALID_PASSWORD("AUTH-011", "비밀번호가 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    ACCOUNT_NOT_ACTIVE("AUTH-012", "활성 상태가 아닌 계정입니다", HttpStatus.FORBIDDEN),

    // 비밀번호 변경
    PASSWORD_CHANGE_FAILED("AUTH-020", "비밀번호 변경에 실패했습니다", HttpStatus.BAD_GATEWAY),
    SAME_PASSWORD("AUTH-021", "현재 비밀번호와 동일합니다", HttpStatus.BAD_REQUEST),

    // 이메일 변경
    EMAIL_CHANGE_FAILED("AUTH-030", "이메일 변경에 실패했습니다", HttpStatus.BAD_GATEWAY),

    // 탈퇴
    WITHDRAW_KEYCLOAK_FAILED("AUTH-040", "Keycloak 사용자 비활성화에 실패했습니다", HttpStatus.BAD_GATEWAY),

    // 이메일 인증
    VERIFICATION_CODE_EXPIRED("AUTH-050", "인증 코드가 만료되었습니다", HttpStatus.BAD_REQUEST),
    VERIFICATION_CODE_INVALID("AUTH-051", "인증 코드가 일치하지 않습니다", HttpStatus.BAD_REQUEST),
    EMAIL_ALREADY_VERIFIED("AUTH-052", "이미 인증된 이메일입니다", HttpStatus.CONFLICT),
    VERIFICATION_NOT_FOUND("AUTH-053", "인증 요청을 찾을 수 없습니다", HttpStatus.NOT_FOUND),

    // Keycloak 통신
    KEYCLOAK_COMMUNICATION_ERROR("AUTH-090", "Keycloak 서버와 통신에 실패했습니다", HttpStatus.BAD_GATEWAY),

    // 프로필 서비스 통신
    PROFILE_SERVICE_ERROR("AUTH-091", "프로필 서비스와 통신에 실패했습니다", HttpStatus.BAD_GATEWAY);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
