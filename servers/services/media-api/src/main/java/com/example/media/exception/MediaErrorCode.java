package com.example.media.exception;

import com.example.core.exception.DomainErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MediaErrorCode implements DomainErrorCode {

    MEDIA_NOT_FOUND("MEDIA-001", "미디어 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INVALID_MEDIA_REQUEST("MEDIA-002", "미디어 요청 값이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    FILE_SIZE_EXCEEDED("MEDIA-003", "허용 가능한 파일 크기를 초과했습니다.", HttpStatus.BAD_REQUEST),
    INVALID_UPLOAD_TOKEN("MEDIA-004", "업로드 확정 토큰이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    EXPIRED_UPLOAD_TOKEN("MEDIA-005", "업로드 확정 토큰이 만료되었습니다.", HttpStatus.BAD_REQUEST),
    FORBIDDEN_MEDIA_ACCESS("MEDIA-006", "미디어 접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    MEDIA_S3_PRESIGN_FAILED("MEDIA-007", "S3 업로드 URL 발급에 실패했습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    MEDIA_S3_OBJECT_NOT_FOUND("MEDIA-008", "S3 업로드 객체를 찾을 수 없습니다.", HttpStatus.BAD_REQUEST),
    MEDIA_S3_HEAD_FAILED("MEDIA-009", "S3 객체 검증 처리에 실패했습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    INVALID_MEDIA_BINDING("MEDIA-010", "미디어 소유자/용도 바인딩 값이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    MEDIA_S3_METADATA_MISMATCH("MEDIA-011", "업로드 객체 메타데이터가 요청값과 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    MEDIA_NOT_READY("MEDIA-012", "미디어가 조회 가능한 상태가 아닙니다.", HttpStatus.CONFLICT),
    MEDIA_LINK_NOT_FOUND("MEDIA-013", "미디어 링크를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INVALID_MEDIA_SYNC_REQUEST("MEDIA-014", "미디어 동기화 요청이 유효하지 않습니다.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
