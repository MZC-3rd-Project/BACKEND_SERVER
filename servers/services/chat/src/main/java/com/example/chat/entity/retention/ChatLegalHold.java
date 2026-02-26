package com.example.chat.entity.retention;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_legal_holds", indexes = {
        @Index(name = "idx_chat_legal_holds_target_type_id", columnList = "target_type, target_id"),
        @Index(name = "idx_chat_legal_holds_active_created", columnList = "active, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatLegalHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private ChatLegalHoldTargetType targetType;

    @Column(name = "target_id", nullable = false, length = 100)
    private String targetId;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    public static ChatLegalHold create(ChatLegalHoldTargetType targetType,
                                       String targetId,
                                       String reason,
                                       Long createdBy) {
        ChatLegalHold legalHold = new ChatLegalHold();
        legalHold.targetType = targetType;
        legalHold.targetId = targetId;
        legalHold.reason = reason;
        legalHold.active = true;
        legalHold.createdBy = createdBy;
        return legalHold;
    }

    public void release() {
        this.active = false;
        this.releasedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
