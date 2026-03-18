package com.example.chat.repository;

import com.example.chat.entity.message.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);

    @Query("""
            SELECT m
            FROM ChatMessage m
            WHERE m.id IN (
                SELECT MAX(innerMessage.id)
                FROM ChatMessage innerMessage
                WHERE innerMessage.roomId IN :roomIds
                GROUP BY innerMessage.roomId
            )
            """)
    List<ChatMessage> findLatestByRoomIdIn(@Param("roomIds") List<Long> roomIds);

    @Query("SELECT m FROM ChatMessage m WHERE m.roomId = :roomId AND m.id < :cursor ORDER BY m.id DESC")
    List<ChatMessage> findByRoomIdAndIdLessThanOrderByIdDesc(@Param("roomId") Long roomId,
                                                             @Param("cursor") Long cursor,
                                                             Pageable pageable);

    @Query("SELECT m FROM ChatMessage m WHERE m.roomId = :roomId AND m.id > :lastReceivedId ORDER BY m.id ASC")
    List<ChatMessage> findByRoomIdAndIdGreaterThanOrderByIdAsc(@Param("roomId") Long roomId,
                                                                @Param("lastReceivedId") Long lastReceivedId,
                                                                Pageable pageable);

    Optional<ChatMessage> findByRoomIdAndSenderIdAndClientMessageId(Long roomId, Long senderId, String clientMessageId);

    long countByRoomId(Long roomId);

    long countByRoomIdAndIdGreaterThan(Long roomId, Long id);

    boolean existsByRoomIdAndId(Long roomId, Long id);

    @Modifying
    @Query(value = """
            DELETE FROM chat_messages m
            WHERE m.created_at < :cutoff
              AND NOT EXISTS (
                SELECT 1
                FROM chat_legal_holds h
                WHERE h.active = TRUE
                  AND (
                    (h.target_type = 'MESSAGE' AND h.target_id = CAST(m.id AS VARCHAR))
                    OR (h.target_type = 'ROOM' AND h.target_id = CAST(m.room_id AS VARCHAR))
                    OR (h.target_type = 'USER' AND h.target_id = CAST(m.sender_id AS VARCHAR))
                  )
              )
            """, nativeQuery = true)
    int hardDeleteExpiredMessagesWithoutLegalHold(@Param("cutoff") LocalDateTime cutoff);
}
