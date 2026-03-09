package com.example.config.kafka;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    Optional<ProcessedEvent> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO processed_events (
                event_id,
                event_type,
                status,
                processed_at,
                error_message
            )
            VALUES (
                :eventId,
                :eventType,
                :status,
                NULL,
                NULL
            )
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertProcessingIgnoreDuplicate(@Param("eventId") String eventId,
                                        @Param("eventType") String eventType,
                                        @Param("status") String status);

    @Modifying
    @Query("UPDATE ProcessedEvent p SET p.status = :newStatus, p.errorMessage = null, p.processedAt = null " +
            "WHERE p.eventId = :eventId AND p.status = :currentStatus")
    int updateStatusIfCurrent(@Param("eventId") String eventId,
                              @Param("currentStatus") ProcessedEvent.ProcessingStatus currentStatus,
                              @Param("newStatus") ProcessedEvent.ProcessingStatus newStatus);
}
