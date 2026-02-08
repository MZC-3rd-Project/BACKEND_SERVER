package com.example.core.pagination;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * 커서 기반 페이지네이션 응답.
 *
 * 페이지네이션된 아이템과 다음 페이지 커서를 포함한다.
 * 커서는 클라이언트에게 불투명하며 다음 페이지 조회에만 사용해야 한다.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CursorResponse<T> {

    /**
     * 현재 페이지 아이템 목록.
     */
    private final List<T> items;

    /**
     * 다음 페이지 커서. 마지막 페이지면 null.
     */
    private final String nextCursor;

    /**
     * 다음 페이지 존재 여부.
     */
    private final boolean hasNext;

    /**
     * 전체 아이템 수 (선택). 대용량에서는 성능 비용이 크므로 꼭 필요할 때만 사용.
     */
    private final Long totalCount;

    /**
     * 현재 페이지 아이템 수.
     */
    private final int size;

    /**
     * 커서 응답 생성.
     */
    public static <T> CursorResponse<T> of(List<T> items, String nextCursor) {
        return of(items, nextCursor, null);
    }

    /**
     * 전체 개수 포함 커서 응답 생성.
     */
    public static <T> CursorResponse<T> of(List<T> items, String nextCursor, Long totalCount) {
        List<T> safeItems = items != null ? items : Collections.emptyList();

        return CursorResponse.<T>builder()
                .items(safeItems)
                .nextCursor(nextCursor)
                .hasNext(nextCursor != null)
                .totalCount(totalCount)
                .size(safeItems.size())
                .build();
    }

    /**
     * 빈 커서 응답 생성.
     */
    public static <T> CursorResponse<T> empty() {
        return CursorResponse.<T>builder()
                .items(Collections.emptyList())
                .nextCursor(null)
                .hasNext(false)
                .totalCount(null)
                .size(0)
                .build();
    }

    /**
     * 마지막 페이지인지 확인.
     */
    public boolean isLastPage() {
        return !hasNext;
    }
}
