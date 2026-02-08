package com.example.core.pagination;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OffsetPageRequestTest {

    @Test
    void shouldCreateOffsetPageRequestWithValidParameters() {
        OffsetPageRequest request = OffsetPageRequest.of(2, 50);

        assertEquals(2, request.getPage());
        assertEquals(50, request.getSize());
        assertEquals(100, request.getOffset());
        assertFalse(request.isFirstPage());
    }

    @Test
    void shouldCreateOffsetPageRequestWithDefaultSize() {
        OffsetPageRequest request = OffsetPageRequest.of(3);

        assertEquals(3, request.getPage());
        assertEquals(20, request.getSize());
        assertEquals(60, request.getOffset());
    }

    @Test
    void shouldCreateFirstPageRequest() {
        OffsetPageRequest request = OffsetPageRequest.firstPage(30);

        assertEquals(0, request.getPage());
        assertEquals(30, request.getSize());
        assertEquals(0, request.getOffset());
        assertTrue(request.isFirstPage());
    }

    @Test
    void shouldCreateFirstPageRequestWithDefaultSize() {
        OffsetPageRequest request = OffsetPageRequest.firstPage();

        assertEquals(0, request.getPage());
        assertEquals(20, request.getSize());
        assertEquals(0, request.getOffset());
        assertTrue(request.isFirstPage());
    }

    @Test
    void shouldThrowExceptionWhenPageIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> OffsetPageRequest.of(-1, 20));
    }

    @Test
    void shouldThrowExceptionWhenSizeIsZero() {
        assertThrows(IllegalArgumentException.class, () -> OffsetPageRequest.of(0, 0));
    }

    @Test
    void shouldThrowExceptionWhenSizeIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> OffsetPageRequest.of(0, -1));
    }

    @Test
    void shouldThrowExceptionWhenSizeExceedsMaximum() {
        assertThrows(IllegalArgumentException.class, () -> OffsetPageRequest.of(0, 101));
    }

    @Test
    void shouldAllowMaximumSize() {
        OffsetPageRequest request = OffsetPageRequest.of(0, 100);

        assertEquals(100, request.getSize());
    }

    @Test
    void shouldCalculateCorrectOffset() {
        assertEquals(0, OffsetPageRequest.of(0, 20).getOffset());
        assertEquals(20, OffsetPageRequest.of(1, 20).getOffset());
        assertEquals(40, OffsetPageRequest.of(2, 20).getOffset());
        assertEquals(100, OffsetPageRequest.of(5, 20).getOffset());
    }

    @Test
    void shouldIdentifyFirstPage() {
        assertTrue(OffsetPageRequest.of(0, 20).isFirstPage());
        assertFalse(OffsetPageRequest.of(1, 20).isFirstPage());
        assertFalse(OffsetPageRequest.of(5, 20).isFirstPage());
    }
}
