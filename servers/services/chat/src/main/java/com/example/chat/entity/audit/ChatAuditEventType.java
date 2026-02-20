package com.example.chat.entity.audit;

public enum ChatAuditEventType {
    ROOM_CREATED,
    ROOM_READ_ONLY_CHANGED,
    PARTICIPANT_ADDED,
    PARTICIPANT_STATUS_CHANGED,
    PARTICIPANT_ROLE_CHANGED,
    NOTICE_SENT,
    RETENTION_DELETED
}
