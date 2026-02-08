package com.example.core.pagination;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CursorUtilsTest {

    @Test
    void shouldEncodeLongId() {
        String cursor = CursorUtils.encode(12345L);

        assertNotNull(cursor);
        assertFalse(cursor.isEmpty());
        // Verify it's URL-safe (no padding)
        assertFalse(cursor.contains("="));
    }

    @Test
    void shouldEncodeStringValue() {
        String cursor = CursorUtils.encode("test-value");

        assertNotNull(cursor);
        assertFalse(cursor.isEmpty());
        assertFalse(cursor.contains("="));
    }

    @Test
    void shouldEncodeAndDecodeLongId() {
        Long originalId = 98765L;
        String cursor = CursorUtils.encode(originalId);
        Long decodedId = CursorUtils.decodeLong(cursor);

        assertEquals(originalId, decodedId);
    }

    @Test
    void shouldEncodeAndDecodeString() {
        String originalValue = "test-cursor-value-123";
        String cursor = CursorUtils.encode(originalValue);
        String decodedValue = CursorUtils.decode(cursor);

        assertEquals(originalValue, decodedValue);
    }

    @Test
    void shouldHandleNullInEncodeLong() {
        String cursor = CursorUtils.encode((Long) null);

        assertNull(cursor);
    }

    @Test
    void shouldHandleNullInEncodeString() {
        String cursor = CursorUtils.encode((String) null);

        assertNull(cursor);
    }

    @Test
    void shouldHandleNullInDecode() {
        String decoded = CursorUtils.decode(null);

        assertNull(decoded);
    }

    @Test
    void shouldHandleNullInDecodeLong() {
        Long decoded = CursorUtils.decodeLong(null);

        assertNull(decoded);
    }

    @Test
    void shouldThrowExceptionForInvalidCursor() {
        assertThrows(IllegalArgumentException.class, () -> CursorUtils.decode("invalid!!!cursor"));
    }

    @Test
    void shouldThrowExceptionForNonLongCursor() {
        String cursor = CursorUtils.encode("not-a-number");

        assertThrows(IllegalArgumentException.class, () -> CursorUtils.decodeLong(cursor));
    }

    @Test
    void shouldEncodeUrlSafeCharacters() {
        String cursor = CursorUtils.encode("special/chars+test=value");

        // Should not contain characters that need URL encoding
        assertFalse(cursor.contains("/"));
        assertFalse(cursor.contains("+"));
        assertFalse(cursor.contains("="));
    }

    @Test
    void shouldHandleUnicodeCharacters() {
        String originalValue = "测试中文字符";
        String cursor = CursorUtils.encode(originalValue);
        String decodedValue = CursorUtils.decode(cursor);

        assertEquals(originalValue, decodedValue);
    }

    @Test
    void shouldHandleVeryLargeId() {
        Long largeId = Long.MAX_VALUE;
        String cursor = CursorUtils.encode(largeId);
        Long decodedId = CursorUtils.decodeLong(cursor);

        assertEquals(largeId, decodedId);
    }

    @Test
    void shouldHandleZeroId() {
        Long zeroId = 0L;
        String cursor = CursorUtils.encode(zeroId);
        Long decodedId = CursorUtils.decodeLong(cursor);

        assertEquals(zeroId, decodedId);
    }

    @Test
    void shouldHandleNegativeId() {
        Long negativeId = -12345L;
        String cursor = CursorUtils.encode(negativeId);
        Long decodedId = CursorUtils.decodeLong(cursor);

        assertEquals(negativeId, decodedId);
    }
}
