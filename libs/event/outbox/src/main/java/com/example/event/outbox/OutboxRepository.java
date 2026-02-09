package com.example.event.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

    @Query("SELECT o FROM OutboxMessage o WHERE o.status = :status AND o.createdAt < :before ORDER BY o.createdAt ASC")
    List<OutboxMessage> findByStatusAndCreatedBefore(
            @Param("status") OutboxStatus status,
            @Param("before") LocalDateTime before);

    @Query(value = "SELECT * FROM outbox_messages WHERE status = :status AND created_at < :before " +
            "ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxMessage> findPendingMessagesForRelay(
            @Param("status") String status,
            @Param("before") LocalDateTime before,
            @Param("limit") int limit);

    @Modifying
    @Query("UPDATE OutboxMessage o SET o.status = :newStatus WHERE o.id = :id AND o.status = :currentStatus")
    int updateStatusById(@Param("id") Long id,
                         @Param("currentStatus") OutboxStatus currentStatus,
                         @Param("newStatus") OutboxStatus newStatus);

    List<OutboxMessage> findByStatusOrderByCreatedAtAsc(OutboxStatus status);

    @Modifying
    @Query("DELETE FROM OutboxMessage o WHERE o.status = :status AND o.createdAt < :before")
    int deleteByStatusAndCreatedBefore(
            @Param("status") OutboxStatus status,
            @Param("before") LocalDateTime before);
}
