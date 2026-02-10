package com.example.product.exception;

import com.example.core.exception.DomainErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements DomainErrorCode {

    // Item
    ITEM_NOT_FOUND("PRODUCT-001", "상품을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    ITEM_ALREADY_EXISTS("PRODUCT-002", "이미 존재하는 상품입니다", HttpStatus.CONFLICT),
    INVALID_ITEM_STATUS_TRANSITION("PRODUCT-003", "유효하지 않은 상품 상태 전이입니다", HttpStatus.BAD_REQUEST),
    ITEM_NOT_EDITABLE("PRODUCT-004", "수정할 수 없는 상태의 상품입니다", HttpStatus.BAD_REQUEST),
    ITEM_NOT_DELETABLE("PRODUCT-005", "삭제할 수 없는 상태의 상품입니다", HttpStatus.BAD_REQUEST),

    // Category
    CATEGORY_NOT_FOUND("PRODUCT-101", "카테고리를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    CATEGORY_DEPTH_EXCEEDED("PRODUCT-102", "카테고리 최대 깊이를 초과했습니다", HttpStatus.BAD_REQUEST),
    CATEGORY_HAS_CHILDREN("PRODUCT-103", "하위 카테고리가 있어 삭제할 수 없습니다", HttpStatus.CONFLICT),

    // Performance
    PERFORMANCE_NOT_FOUND("PRODUCT-201", "공연 정보를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    INVALID_PERFORMANCE_DATE("PRODUCT-202", "유효하지 않은 공연 일시입니다", HttpStatus.BAD_REQUEST),

    // Seat Grade
    SEAT_GRADE_NOT_FOUND("PRODUCT-301", "좌석 등급을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    DUPLICATE_SEAT_GRADE("PRODUCT-302", "중복된 좌석 등급입니다", HttpStatus.CONFLICT),
    INVALID_FUNDING_QUANTITY("PRODUCT-303", "펀딩 수량은 총 수량을 초과할 수 없습니다", HttpStatus.BAD_REQUEST),

    // Goods Link
    INVALID_LINK_TARGET("PRODUCT-402", "연결 대상 상품이 유효하지 않습니다", HttpStatus.BAD_REQUEST),

    // Goods / Product
    GOODS_NOT_FOUND("PRODUCT-401", "굿즈 정보를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    PRODUCT_OPTION_NOT_FOUND("PRODUCT-501", "상품 옵션을 찾을 수 없습니다", HttpStatus.NOT_FOUND),

    // Image
    IMAGE_NOT_FOUND("PRODUCT-601", "이미지를 찾을 수 없습니다", HttpStatus.NOT_FOUND),

    // Authorization
    UNAUTHORIZED_ACCESS("PRODUCT-901", "해당 상품에 대한 권한이 없습니다", HttpStatus.FORBIDDEN);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
