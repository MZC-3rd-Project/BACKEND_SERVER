package com.example.config.kafka;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DeadLetterMessageRepository extends JpaRepository<DeadLetterMessage, Long> {

    List<DeadLetterMessage> findByTopicOrderByCreatedAtAsc(String topic);

    Optional<DeadLetterMessage> findFirstByEventIdAndStatusInOrderByCreatedAtDesc(
            String eventId,
            Set<DeadLetterMessage.DlqStatus> statuses
    );

    @Query("""
            SELECT d
            FROM DeadLetterMessage d
            WHERE d.status IN :statuses
              AND d.eventId IS NOT NULL
            ORDER BY d.createdAt ASC
            """)
    List<DeadLetterMessage> findActiveMessagesWithEventId(@Param("statuses") Set<DeadLetterMessage.DlqStatus> statuses,
                                                          Pageable pageable);

    @Query("""
            SELECT d
            FROM DeadLetterMessage d
            WHERE d.status IN :statuses
              AND d.eventId IS NOT NULL
              AND (d.nextRetryAt IS NULL OR d.nextRetryAt <= :now)
            ORDER BY d.nextRetryAt ASC, d.createdAt ASC
            """)
    List<DeadLetterMessage> findRetryTargets(@Param("statuses") Set<DeadLetterMessage.DlqStatus> statuses,
                                             @Param("now") LocalDateTime now,
                                             Pageable pageable);
}
