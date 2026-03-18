package com.example.chat.repository;

import com.example.chat.entity.participant.ChatParticipantStatus;
import com.example.chat.entity.participant.ChatRoomParticipant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomParticipantRepository extends JpaRepository<ChatRoomParticipant, Long> {

    Optional<ChatRoomParticipant> findByRoomIdAndUserId(Long roomId, Long userId);

    List<ChatRoomParticipant> findByRoomIdOrderByIdAsc(Long roomId);

    List<ChatRoomParticipant> findByRoomIdInOrderByRoomIdAscIdAsc(List<Long> roomIds);

    List<ChatRoomParticipant> findByUserIdAndStatusOrderByRoomIdDesc(
            Long userId,
            ChatParticipantStatus status,
            Pageable pageable
    );

    @Query("SELECT p FROM ChatRoomParticipant p WHERE p.userId = :userId AND p.status = :status " +
            "AND p.roomId < :cursor ORDER BY p.roomId DESC")
    List<ChatRoomParticipant> findByUserIdAndStatusAndRoomIdLessThanOrderByRoomIdDesc(
            @Param("userId") Long userId,
            @Param("status") ChatParticipantStatus status,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    List<ChatRoomParticipant> findByUserIdAndStatusOrderByRoomIdDesc(Long userId, ChatParticipantStatus status);

    List<ChatRoomParticipant> findByRoomIdAndStatusOrderByIdAsc(Long roomId, ChatParticipantStatus status);

    @Query(value = """
            SELECT p.*
            FROM chat_room_participants p
            LEFT JOIN (
                SELECT room_id, MAX(id) AS last_message_id
                FROM chat_messages
                WHERE deleted_at IS NULL
                GROUP BY room_id
            ) latest_message ON latest_message.room_id = p.room_id
            WHERE p.user_id = :userId
              AND p.status = :status
              AND p.deleted_at IS NULL
              AND (
                    :cursorLastMessageId IS NULL
                    OR COALESCE(latest_message.last_message_id, 0) < :cursorLastMessageId
                    OR (
                        COALESCE(latest_message.last_message_id, 0) = :cursorLastMessageId
                        AND p.room_id < :cursorRoomId
                    )
              )
            ORDER BY COALESCE(latest_message.last_message_id, 0) DESC, p.room_id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ChatRoomParticipant> findActiveParticipantsOrderByLatestMessage(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("cursorLastMessageId") Long cursorLastMessageId,
            @Param("cursorRoomId") Long cursorRoomId,
            @Param("limit") int limit
    );
}
