package com.example.review.exception;

import com.example.core.exception.DomainErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReviewErrorCode implements DomainErrorCode {

    REVIEW_NOT_FOUND("REVIEW-001", "리뷰를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    REVIEW_NOT_ELIGIBLE("REVIEW-002", "리뷰를 작성할 수 없는 주문입니다", HttpStatus.BAD_REQUEST),
    REVIEW_ALREADY_EXISTS("REVIEW-003", "이미 작성한 리뷰입니다", HttpStatus.CONFLICT),
    INVALID_MEDIA_REFERENCE("REVIEW-004", "유효하지 않은 리뷰 이미지입니다", HttpStatus.BAD_REQUEST),
    MEDIA_SERVICE_ERROR("REVIEW-005", "미디어 서비스 연동에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    ORDER_SERVICE_ERROR("REVIEW-006", "주문 서비스 검증에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    PRODUCT_SERVICE_ERROR("REVIEW-007", "상품 서비스 평점 반영에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    ITEM_NOT_FOUND("REVIEW-008", "상품을 찾을 수 없습니다", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
