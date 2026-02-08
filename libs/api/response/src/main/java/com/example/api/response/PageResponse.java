package com.example.api.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PageResponse<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean hasNext;
    private final boolean hasPrevious;

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return PageResponse.<T>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .hasNext(page < totalPages - 1)
                .hasPrevious(page > 0)
                .build();
    }

    public static <T> PageResponse<T> from(Object springDataPage) {
        try {
            Class<?> pageClass = springDataPage.getClass();

            @SuppressWarnings("unchecked")
            List<T> content = (List<T>) pageClass.getMethod("getContent").invoke(springDataPage);
            int number = (int) pageClass.getMethod("getNumber").invoke(springDataPage);
            int pageSize = (int) pageClass.getMethod("getSize").invoke(springDataPage);
            long total = (long) pageClass.getMethod("getTotalElements").invoke(springDataPage);
            int pages = (int) pageClass.getMethod("getTotalPages").invoke(springDataPage);
            boolean next = (boolean) pageClass.getMethod("hasNext").invoke(springDataPage);
            boolean previous = (boolean) pageClass.getMethod("hasPrevious").invoke(springDataPage);

            return PageResponse.<T>builder()
                    .content(content)
                    .page(number)
                    .size(pageSize)
                    .totalElements(total)
                    .totalPages(pages)
                    .hasNext(next)
                    .hasPrevious(previous)
                    .build();
        } catch (Exception e) {
            throw new IllegalArgumentException("Spring Data Page 객체 변환에 실패했습니다", e);
        }
    }

    public boolean isFirst() {
        return !hasPrevious;
    }

    public boolean isLast() {
        return !hasNext;
    }

    public boolean isEmpty() {
        return content == null || content.isEmpty();
    }
}
