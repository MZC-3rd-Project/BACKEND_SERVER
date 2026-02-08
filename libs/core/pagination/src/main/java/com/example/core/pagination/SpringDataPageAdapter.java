package com.example.core.pagination;

import org.springframework.data.domain.Page;

/**
 * Spring Data Page → OffsetPageResponse 변환 어댑터.
 *
 * Spring Data가 클래스패스에 있을 때만 사용 가능.
 * ClassNotFoundException 방지를 위해 별도 클래스로 분리.
 */
public final class SpringDataPageAdapter {

    private SpringDataPageAdapter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Spring Data Page를 OffsetPageResponse로 변환.
     */
    public static <T> OffsetPageResponse<T> fromPage(Page<T> page) {
        if (page == null) {
            return OffsetPageResponse.empty();
        }

        return OffsetPageResponse.of(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }
}
