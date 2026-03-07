package com.example.store.exception;

import com.example.core.exception.DomainErrorCode;
import com.example.core.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StoreErrorCode implements DomainErrorCode {

    // Merchant
    MERCHANT_NOT_FOUND("STORE-001", "가게를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    MERCHANT_ALREADY_EXISTS("STORE-002", "이미 등록된 가게입니다", HttpStatus.CONFLICT),
    MERCHANT_SUSPENDED("STORE-003", "정지된 가게입니다", HttpStatus.FORBIDDEN),
    MERCHANT_INACTIVE("STORE-004", "비활성화된 가게입니다", HttpStatus.FORBIDDEN),
    MERCHANT_USER_ALREADY_REGISTERED("STORE-005", "해당 유저는 이미 가게를 보유하고 있습니다", HttpStatus.CONFLICT),
    INVALID_MERCHANT_STATUS("STORE-006", "유효하지 않은 가게 상태입니다", HttpStatus.BAD_REQUEST),

    // Address
    ADDRESS_NOT_FOUND("STORE-101", "주소를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    INVALID_ADDRESS_TYPE("STORE-102", "유효하지 않은 주소 타입입니다", HttpStatus.BAD_REQUEST),
    ADDRESS_ALREADY_EXISTS("STORE-103", "이미 등록된 주소입니다", HttpStatus.CONFLICT),

    // Contact
    CONTACT_NOT_FOUND("STORE-201", "연락처를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    INVALID_CONTACT_TYPE("STORE-202", "유효하지 않은 연락처 타입입니다", HttpStatus.BAD_REQUEST),
    CONTACT_ALREADY_EXISTS("STORE-203", "이미 등록된 연락처입니다", HttpStatus.CONFLICT),
    PRIMARY_CONTACT_REQUIRED("STORE-204", "대표 연락처는 필수입니다", HttpStatus.BAD_REQUEST),
    DUPLICATE_PRIMARY_CONTACT("STORE-205", "대표 연락처는 하나만 등록 가능합니다", HttpStatus.CONFLICT),

    // Profile
    PROFILE_NOT_FOUND("STORE-301", "가게 프로필을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    PROFILE_ALREADY_EXISTS("STORE-302", "가게 프로필이 이미 존재합니다", HttpStatus.CONFLICT),

    // Image
    IMAGE_NOT_FOUND("STORE-401", "이미지를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    INVALID_IMAGE_TYPE("STORE-402", "유효하지 않은 이미지 타입입니다", HttpStatus.BAD_REQUEST),
    IMAGE_UPLOAD_FAILED("STORE-403", "이미지 업로드에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    IMAGE_LIMIT_EXCEEDED("STORE-404", "이미지 등록 가능 수를 초과했습니다", HttpStatus.BAD_REQUEST),
    THUMBNAIL_REQUIRED("STORE-405", "썸네일 이미지는 필수입니다", HttpStatus.BAD_REQUEST),
    DUPLICATE_THUMBNAIL("STORE-406", "썸네일 이미지는 하나만 등록 가능합니다", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
