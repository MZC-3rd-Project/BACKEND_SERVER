package com.example.core.pagination;

import lombok.Builder;
import lombok.Getter;

/**
 * 전통적인 오프셋 기반 페이지네이션 요청.
 *
 * 오프셋 방식의 알려진 이슈:
 * - 큰 오프셋에서 성능 저하 (OFFSET N LIMIT M은 N이 커질수록 느려짐)
 * - 데이터 변경 시 결과 불일치 (아이템 누락/중복 가능)
 * - 무한 스크롤이나 실시간 피드에 부적합
 *
 * 성능과 일관성이 필요하면 CursorRequest 사용을 권장.
 */
@Getter
@Builder
public class OffsetPageRequest {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    /**
     * 페이지 번호 (0부터 시작). 기본값: 0
     */
    @Builder.Default
    private final int page = DEFAULT_PAGE;

    /**
     * 페이지 당 아이템 수. 기본값: 20, 최대: 100
     */
    @Builder.Default
    private final int size = DEFAULT_SIZE;

    /**
     * 검증된 오프셋 요청 생성.
     */
    public static OffsetPageRequest of(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page must be greater than or equal to 0");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be greater than 0");
        }
        if (size > MAX_SIZE) {
            throw new IllegalArgumentException("Size must not exceed " + MAX_SIZE);
        }

        return OffsetPageRequest.builder()
                .page(page)
                .size(size)
                .build();
    }

    /**
     * 기본 사이즈로 오프셋 요청 생성.
     */
    public static OffsetPageRequest of(int page) {
        return of(page, DEFAULT_SIZE);
    }

    /**
     * 첫 페이지 요청 생성.
     */
    public static OffsetPageRequest firstPage(int size) {
        return of(DEFAULT_PAGE, size);
    }

    /**
     * 기본 사이즈로 첫 페이지 요청 생성.
     */
    public static OffsetPageRequest firstPage() {
        return of(DEFAULT_PAGE, DEFAULT_SIZE);
    }

    /**
     * DB 쿼리용 오프셋 계산 (page * size).
     */
    public int getOffset() {
        return page * size;
    }

    /**
     * 첫 페이지 요청인지 확인.
     */
    public boolean isFirstPage() {
        return page == 0;
    }
}
