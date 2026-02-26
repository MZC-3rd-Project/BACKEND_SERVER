package com.example.profile.exception;

import com.example.core.exception.DomainErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProfileErrorCode implements DomainErrorCode {
    // Profile
    PROFILE_NOT_FOUND("PROFILE-001", "프로필을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    PROFILE_ALREADY_EXISTS("PROFILE-002", "이미 존재하는 프로필입니다", HttpStatus.CONFLICT),
    PROFILE_NOT_EDITABLE("PROFILE-003", "수정할 수 없는 상태의 프로필입니다", HttpStatus.BAD_REQUEST),
    PROFILE_NOT_DELETABLE("PROFILE-004", "삭제할 수 없는 상태의 프로필입니다", HttpStatus.BAD_REQUEST),

    // Profile Image (mediaId 관리)
    PROFILE_IMAGE_NOT_SET("PROFILE-101", "프로필 이미지가 설정되어 있지 않습니다", HttpStatus.NOT_FOUND),
    INVALID_MEDIA_ID("PROFILE-102", "유효하지 않은 mediaId입니다", HttpStatus.BAD_REQUEST),
    MEDIA_NOT_FOUND("PROFILE-103", "존재하지 않는 프로필 입니다", HttpStatus.BAD_REQUEST),
  
    // Media Service 연동
    MEDIA_SERVICE_COMMUNICATION_ERROR("PROFILE-151", "Media 서비스와 통신 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    MEDIA_VALIDATION_FAILED("PROFILE-152", "Media 검증에 실패했습니다", HttpStatus.BAD_REQUEST),

    // Authorization
    UNAUTHORIZED_PROFILE_ACCESS("PROFILE-901", "해당 프로필에 대한 권한이 없습니다", HttpStatus.FORBIDDEN);



    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
