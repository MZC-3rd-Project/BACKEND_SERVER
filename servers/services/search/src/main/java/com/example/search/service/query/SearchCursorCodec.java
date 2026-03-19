package com.example.search.service.query;

import com.example.core.pagination.CursorUtils;

final class SearchCursorCodec {

    private SearchCursorCodec() {
    }

    static String encodeOffset(int offset) {
        return CursorUtils.encode(Integer.toString(offset));
    }

    static int decodeOffset(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }

        String decoded = CursorUtils.decode(cursor);
        try {
            int offset = Integer.parseInt(decoded);
            if (offset < 0) {
                throw new IllegalArgumentException("cursor는 0 이상이어야 합니다");
            }
            return offset;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("유효하지 않은 cursor 형식입니다", exception);
        }
    }
}
