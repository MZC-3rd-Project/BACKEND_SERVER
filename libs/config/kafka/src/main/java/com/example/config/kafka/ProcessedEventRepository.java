package com.example.config.kafka;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    Optional<ProcessedEvent> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    @Modifying
    @Query("UPDATE ProcessedEvent p SET p.status = :newStatus, p.errorMessage = null, p.processedAt = null " +
            "WHERE p.eventId = :eventId AND p.status = :currentStatus")
    int updateStatusIfCurrent(@Param("eventId") String eventId,
                              @Param("currentStatus") ProcessedEvent.ProcessingStatus currentStatus,
                              @Param("newStatus") ProcessedEvent.ProcessingStatus newStatus);
}
