package com.example.chat.repository;

import com.example.chat.entity.retention.ChatLegalHold;
import com.example.chat.entity.retention.ChatLegalHoldTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatLegalHoldRepository extends JpaRepository<ChatLegalHold, Long> {

    Optional<ChatLegalHold> findByTargetTypeAndTargetIdAndActiveTrue(ChatLegalHoldTargetType targetType, String targetId);
}
