package com.example.core.pagination;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CursorRequestTest {

    @Test
    void shouldCreateCursorRequestWithValidSize() {
        CursorRequest request = CursorRequest.of("cursor123", 50);

        assertEquals("cursor123", request.getCursor());
        assertEquals(50, request.getSize());
        assertFalse(request.isFirstPage());
    }

    @Test
    void shouldCreateCursorRequestWithDefaultSize() {
        CursorRequest request = CursorRequest.of("cursor123");

        assertEquals("cursor123", request.getCursor());
        assertEquals(20, request.getSize());
    }

    @Test
    void shouldCreateFirstPageRequest() {
        CursorRequest request = CursorRequest.firstPage(30);

        assertNull(request.getCursor());
        assertEquals(30, request.getSize());
        assertTrue(request.isFirstPage());
    }

    @Test
    void shouldCreateFirstPageRequestWithDefaultSize() {
        CursorRequest request = CursorRequest.firstPage();

        assertNull(request.getCursor());
        assertEquals(20, request.getSize());
        assertTrue(request.isFirstPage());
    }

    @Test
    void shouldThrowExceptionWhenSizeIsZero() {
        assertThrows(IllegalArgumentException.class, () -> CursorRequest.of("cursor", 0));
    }

    @Test
    void shouldThrowExceptionWhenSizeIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> CursorRequest.of("cursor", -1));
    }

    @Test
    void shouldThrowExceptionWhenSizeExceedsMaximum() {
        assertThrows(IllegalArgumentException.class, () -> CursorRequest.of("cursor", 101));
    }

    @Test
    void shouldAllowMaximumSize() {
        CursorRequest request = CursorRequest.of("cursor", 100);

        assertEquals(100, request.getSize());
    }

    @Test
    void shouldIdentifyFirstPageWhenCursorIsNull() {
        CursorRequest request = CursorRequest.of(null, 20);

        assertTrue(request.isFirstPage());
    }

    @Test
    void shouldNotIdentifyFirstPageWhenCursorExists() {
        CursorRequest request = CursorRequest.of("cursor", 20);

        assertFalse(request.isFirstPage());
    }
}
