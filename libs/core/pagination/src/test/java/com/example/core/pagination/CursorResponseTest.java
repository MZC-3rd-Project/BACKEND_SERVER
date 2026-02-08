package com.example.core.pagination;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CursorResponseTest {

    @Test
    void shouldCreateCursorResponseWithItems() {
        List<String> items = Arrays.asList("item1", "item2", "item3");
        CursorResponse<String> response = CursorResponse.of(items, "nextCursor");

        assertEquals(items, response.getItems());
        assertEquals("nextCursor", response.getNextCursor());
        assertTrue(response.isHasNext());
        assertFalse(response.isLastPage());
        assertEquals(3, response.getSize());
        assertNull(response.getTotalCount());
    }

    @Test
    void shouldCreateCursorResponseWithTotalCount() {
        List<String> items = Arrays.asList("item1", "item2");
        CursorResponse<String> response = CursorResponse.of(items, "nextCursor", 100L);

        assertEquals(items, response.getItems());
        assertEquals("nextCursor", response.getNextCursor());
        assertTrue(response.isHasNext());
        assertEquals(2, response.getSize());
        assertEquals(100L, response.getTotalCount());
    }

    @Test
    void shouldCreateLastPageResponse() {
        List<String> items = Arrays.asList("item1", "item2");
        CursorResponse<String> response = CursorResponse.of(items, null);

        assertEquals(items, response.getItems());
        assertNull(response.getNextCursor());
        assertFalse(response.isHasNext());
        assertTrue(response.isLastPage());
        assertEquals(2, response.getSize());
    }

    @Test
    void shouldCreateEmptyResponse() {
        CursorResponse<String> response = CursorResponse.empty();

        assertTrue(response.getItems().isEmpty());
        assertNull(response.getNextCursor());
        assertFalse(response.isHasNext());
        assertTrue(response.isLastPage());
        assertEquals(0, response.getSize());
        assertNull(response.getTotalCount());
    }

    @Test
    void shouldHandleNullItemsList() {
        CursorResponse<String> response = CursorResponse.of(null, "cursor");

        assertNotNull(response.getItems());
        assertTrue(response.getItems().isEmpty());
        assertEquals(0, response.getSize());
    }

    @Test
    void shouldCalculateHasNextFromCursor() {
        CursorResponse<String> withNext = CursorResponse.of(
                Arrays.asList("item1"),
                "cursor"
        );
        CursorResponse<String> withoutNext = CursorResponse.of(
                Arrays.asList("item1"),
                null
        );

        assertTrue(withNext.isHasNext());
        assertFalse(withoutNext.isHasNext());
    }
}
