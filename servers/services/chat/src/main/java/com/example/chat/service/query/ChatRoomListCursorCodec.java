package com.example.chat.service.query;

import com.example.core.pagination.CursorUtils;

final class ChatRoomListCursorCodec {

    private ChatRoomListCursorCodec() {
    }

    static String encode(Long lastMessageId, Long roomId) {
        long safeLastMessageId = lastMessageId == null ? 0L : lastMessageId;
        return CursorUtils.encode(safeLastMessageId + "|" + roomId);
    }

    static Cursor decode(String cursor) {
        if (cursor == null) {
            return null;
        }

        String decoded = CursorUtils.decode(cursor);
        String[] parts = decoded.split("\\|", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid chat room list cursor");
        }

        return new Cursor(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
    }

    record Cursor(Long lastMessageId, Long roomId) {
    }
}
