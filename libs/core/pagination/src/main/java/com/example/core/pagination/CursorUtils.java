package com.example.core.pagination;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 커서 값 인코딩/디코딩 유틸리티.
 *
 * Base64 URL-safe 인코딩(RFC 4648)을 사용하여
 * URL에서 이스케이프 없이 안전하게 사용할 수 있다.
 */
public final class CursorUtils {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private CursorUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Long ID를 커서 문자열로 인코딩.
     */
    public static String encode(Long id) {
        if (id == null) {
            return null;
        }
        return encode(id.toString());
    }

    /**
     * 문자열 값을 커서 문자열로 인코딩.
     */
    public static String encode(String value) {
        if (value == null) {
            return null;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return ENCODER.encodeToString(bytes);
    }

    /**
     * 커서 문자열을 원래 문자열로 디코딩.
     */
    public static String decode(String cursor) {
        if (cursor == null) {
            return null;
        }
        try {
            byte[] bytes = DECODER.decode(cursor);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid cursor format: " + cursor, e);
        }
    }

    /**
     * 커서 문자열을 Long ID로 디코딩.
     */
    public static Long decodeLong(String cursor) {
        String decoded = decode(cursor);
        if (decoded == null) {
            return null;
        }
        try {
            return Long.parseLong(decoded);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cursor does not represent a valid Long: " + cursor, e);
        }
    }
}
