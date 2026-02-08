package com.example.core.pagination;

import lombok.Builder;
import lombok.Getter;

/**
 * 커서 기반 페이지네이션 요청.
 *
 * 오프셋 방식 대비 장점:
 * - 대용량 데이터셋 (OFFSET 성능 저하 없음)
 * - 실시간 데이터 (삽입/삭제에도 일관된 결과)
 * - API 요청 제한 (무상태 커서)
 */
@Getter
@Builder
public class CursorRequest {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    /**
     * 다음 페이지 커서. null이면 첫 페이지.
     */
    private final String cursor;

    /**
     * 페이지 당 아이템 수. 기본값: 20, 최대: 100
     */
    @Builder.Default
    private final int size = DEFAULT_SIZE;

    /**
     * 검증된 커서 요청 생성.
     */
    public static CursorRequest of(String cursor, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be greater than 0");
        }
        if (size > MAX_SIZE) {
            throw new IllegalArgumentException("Size must not exceed " + MAX_SIZE);
        }

        return CursorRequest.builder()
                .cursor(cursor)
                .size(size)
                .build();
    }

    /**
     * 기본 사이즈로 커서 요청 생성.
     */
    public static CursorRequest of(String cursor) {
        return of(cursor, DEFAULT_SIZE);
    }

    /**
     * 첫 페이지 요청 생성.
     */
    public static CursorRequest firstPage(int size) {
        return of(null, size);
    }

    /**
     * 기본 사이즈로 첫 페이지 요청 생성.
     */
    public static CursorRequest firstPage() {
        return of(null, DEFAULT_SIZE);
    }

    /**
     * 첫 페이지 요청인지 확인.
     */
    public boolean isFirstPage() {
        return cursor == null;
    }
}
