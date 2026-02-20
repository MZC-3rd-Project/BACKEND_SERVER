package com.example.chat.repository;

import com.example.chat.entity.message.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);

    @Query("SELECT m FROM ChatMessage m WHERE m.roomId = :roomId AND m.id < :cursor ORDER BY m.id DESC")
    List<ChatMessage> findByRoomIdAndIdLessThanOrderByIdDesc(@Param("roomId") Long roomId,
                                                             @Param("cursor") Long cursor,
                                                             Pageable pageable);

    Optional<ChatMessage> findByRoomIdAndSenderIdAndClientMessageId(Long roomId, Long senderId, String clientMessageId);

    long countByRoomId(Long roomId);

    long countByRoomIdAndIdGreaterThan(Long roomId, Long id);

    boolean existsByRoomIdAndId(Long roomId, Long id);
}
