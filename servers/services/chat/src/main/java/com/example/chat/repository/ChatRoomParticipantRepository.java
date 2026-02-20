package com.example.chat.repository;

import com.example.chat.entity.participant.ChatParticipantStatus;
import com.example.chat.entity.participant.ChatRoomParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomParticipantRepository extends JpaRepository<ChatRoomParticipant, Long> {

    Optional<ChatRoomParticipant> findByRoomIdAndUserId(Long roomId, Long userId);

    List<ChatRoomParticipant> findByRoomIdOrderByIdAsc(Long roomId);

    List<ChatRoomParticipant> findByUserIdAndStatusOrderByRoomIdDesc(Long userId, ChatParticipantStatus status);

    List<ChatRoomParticipant> findByRoomIdAndStatusOrderByIdAsc(Long roomId, ChatParticipantStatus status);
}
