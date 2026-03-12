package com.example.storequery.service.query;

import com.example.core.pagination.CursorUtils;

import java.time.LocalDateTime;

final class StoreQueryCursorCodec {

    private StoreQueryCursorCodec() {
    }

    static String encodeList(LocalDateTime sourceUpdatedAt, Long storeId) {
        return CursorUtils.encode(sourceUpdatedAt + "|" + storeId);
    }

    static ListCursor decodeList(String cursor) {
        if (cursor == null) {
            return null;
        }

        String decoded = CursorUtils.decode(cursor);
        String[] parts = decoded.split("\\|", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid store list cursor");
        }

        return new ListCursor(
                LocalDateTime.parse(parts[0]),
                Long.parseLong(parts[1])
        );
    }

    static String encodeSearch(Double sortRank, LocalDateTime sourceUpdatedAt, Long storeId) {
        return CursorUtils.encode(sortRank + "|" + sourceUpdatedAt + "|" + storeId);
    }

    static SearchCursor decodeSearch(String cursor) {
        if (cursor == null) {
            return null;
        }

        String decoded = CursorUtils.decode(cursor);
        String[] parts = decoded.split("\\|", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid store search cursor");
        }

        return new SearchCursor(
                Double.parseDouble(parts[0]),
                LocalDateTime.parse(parts[1]),
                Long.parseLong(parts[2])
        );
    }

    record ListCursor(LocalDateTime sourceUpdatedAt, Long storeId) {
    }

    record SearchCursor(Double sortRank, LocalDateTime sourceUpdatedAt, Long storeId) {
    }
}
