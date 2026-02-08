package com.example.core.pagination;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OffsetPageResponseTest {

    @Test
    void shouldCreateOffsetPageResponse() {
        List<String> items = Arrays.asList("item1", "item2", "item3");
        OffsetPageResponse<String> response = OffsetPageResponse.of(items, 0, 20, 50);

        assertEquals(items, response.getItems());
        assertEquals(0, response.getPage());
        assertEquals(20, response.getSize());
        assertEquals(50, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
        assertTrue(response.isHasNext());
        assertFalse(response.isHasPrevious());
        assertTrue(response.isFirstPage());
        assertFalse(response.isLastPage());
    }

    @Test
    void shouldCalculateTotalPagesCorrectly() {
        // 50 items, 20 per page = 3 pages (ceil(50/20) = 3)
        OffsetPageResponse<String> response = OffsetPageResponse.of(
                Arrays.asList("item1", "item2"),
                0,
                20,
                50
        );

        assertEquals(3, response.getTotalPages());
    }

    @Test
    void shouldHandleExactDivision() {
        // 60 items, 20 per page = 3 pages (ceil(60/20) = 3)
        OffsetPageResponse<String> response = OffsetPageResponse.of(
                Arrays.asList("item1", "item2"),
                0,
                20,
                60
        );

        assertEquals(3, response.getTotalPages());
    }

    @Test
    void shouldIdentifyFirstPage() {
        OffsetPageResponse<String> response = OffsetPageResponse.of(
                Arrays.asList("item1"),
                0,
                20,
                100
        );

        assertTrue(response.isFirstPage());
        assertFalse(response.isHasPrevious());
    }

    @Test
    void shouldIdentifyMiddlePage() {
        OffsetPageResponse<String> response = OffsetPageResponse.of(
                Arrays.asList("item1"),
                2,
                20,
                100
        );

        assertFalse(response.isFirstPage());
        assertFalse(response.isLastPage());
        assertTrue(response.isHasNext());
        assertTrue(response.isHasPrevious());
    }

    @Test
    void shouldIdentifyLastPage() {
        OffsetPageResponse<String> response = OffsetPageResponse.of(
                Arrays.asList("item1"),
                4,
                20,
                100
        );

        assertTrue(response.isLastPage());
        assertFalse(response.isHasNext());
        assertTrue(response.isHasPrevious());
        assertFalse(response.isFirstPage());
    }

    @Test
    void shouldHandleSinglePage() {
        OffsetPageResponse<String> response = OffsetPageResponse.of(
                Arrays.asList("item1", "item2"),
                0,
                20,
                2
        );

        assertTrue(response.isFirstPage());
        assertTrue(response.isLastPage());
        assertFalse(response.isHasNext());
        assertFalse(response.isHasPrevious());
        assertEquals(1, response.getTotalPages());
    }

    @Test
    void shouldCreateEmptyResponse() {
        OffsetPageResponse<String> response = OffsetPageResponse.empty();

        assertTrue(response.getItems().isEmpty());
        assertEquals(0, response.getPage());
        assertEquals(0, response.getSize());
        assertEquals(0, response.getTotalElements());
        assertEquals(0, response.getTotalPages());
        assertFalse(response.isHasNext());
        assertFalse(response.isHasPrevious());
    }

    @Test
    void shouldHandleNullItemsList() {
        OffsetPageResponse<String> response = OffsetPageResponse.of(null, 0, 20, 0);

        assertNotNull(response.getItems());
        assertTrue(response.getItems().isEmpty());
    }

    @Test
    void shouldHandleZeroTotalElements() {
        OffsetPageResponse<String> response = OffsetPageResponse.of(
                Arrays.asList(),
                0,
                20,
                0
        );

        assertEquals(0, response.getTotalPages());
        assertFalse(response.isHasNext());
        assertTrue(response.isFirstPage());
        assertTrue(response.isLastPage());
    }
}
