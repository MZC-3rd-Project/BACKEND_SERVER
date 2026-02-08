package com.example.event.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

    @Query("SELECT o FROM OutboxMessage o WHERE o.status = :status AND o.createdAt < :before ORDER BY o.createdAt ASC")
    List<OutboxMessage> findByStatusAndCreatedBefore(
            @Param("status") OutboxStatus status,
            @Param("before") LocalDateTime before);

    List<OutboxMessage> findByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
