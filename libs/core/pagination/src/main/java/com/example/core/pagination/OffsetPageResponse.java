package com.example.core.pagination;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * 오프셋 기반 페이지네이션 응답.
 *
 * 페이지네이션된 아이템과 전체 개수 등 메타데이터를 포함한다.
 */
@Getter
@Builder
public class OffsetPageResponse<T> {

    /**
     * 현재 페이지 아이템 목록.
     */
    private final List<T> items;

    /**
     * 현재 페이지 번호 (0부터 시작).
     */
    private final int page;

    /**
     * 페이지 당 아이템 수.
     */
    private final int size;

    /**
     * 전체 아이템 수.
     */
    private final long totalElements;

    /**
     * 전체 페이지 수.
     */
    private final int totalPages;

    /**
     * 다음 페이지 존재 여부.
     */
    private final boolean hasNext;

    /**
     * 이전 페이지 존재 여부.
     */
    private final boolean hasPrevious;

    /**
     * 오프셋 응답 생성.
     */
    public static <T> OffsetPageResponse<T> of(List<T> items, int page, int size, long totalElements) {
        List<T> safeItems = items != null ? items : Collections.emptyList();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        return OffsetPageResponse.<T>builder()
                .items(safeItems)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .hasNext(page < totalPages - 1)
                .hasPrevious(page > 0)
                .build();
    }

    /**
     * 빈 오프셋 응답 생성.
     */
    public static <T> OffsetPageResponse<T> empty() {
        return OffsetPageResponse.<T>builder()
                .items(Collections.emptyList())
                .page(0)
                .size(0)
                .totalElements(0)
                .totalPages(0)
                .hasNext(false)
                .hasPrevious(false)
                .build();
    }

    /**
     * 첫 페이지인지 확인.
     */
    public boolean isFirstPage() {
        return page == 0;
    }

    /**
     * 마지막 페이지인지 확인.
     */
    public boolean isLastPage() {
        return !hasNext;
    }
}
