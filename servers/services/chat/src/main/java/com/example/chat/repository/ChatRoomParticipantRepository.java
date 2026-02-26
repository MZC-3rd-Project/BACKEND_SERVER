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
}
