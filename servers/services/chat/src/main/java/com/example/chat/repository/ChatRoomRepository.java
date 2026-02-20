package com.example.chat.repository;

import com.example.chat.entity.room.ChatRoom;
import com.example.chat.entity.room.ChatRoomStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByRoomKey(String roomKey);

    Optional<ChatRoom> findByCampaignId(Long campaignId);

    List<ChatRoom> findBySellerIdAndStatusInOrderByIdDesc(Long sellerId, Collection<ChatRoomStatus> statuses, Pageable pageable);

    @Query("SELECT r FROM ChatRoom r WHERE r.id < :cursor ORDER BY r.id DESC")
    List<ChatRoom> findByIdLessThan(@Param("cursor") Long cursor, Pageable pageable);
}
