package com.example.event.inbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InboxRepository extends JpaRepository<InboxMessage, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO inbox_messages (
                version,
                consumer_name,
                event_id,
                event_type,
                payload,
                status,
                retry_count,
                next_retry_at,
                lease_until,
                processed_at,
                last_error,
                created_at,
                updated_at,
                deleted_at
            )
            VALUES (
                0,
                :consumerName,
                :eventId,
                :eventType,
                :payload,
                :status,
                0,
                NULL,
                NULL,
                NULL,
                NULL,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP,
                NULL
            )
            ON CONFLICT (consumer_name, event_id) DO NOTHING
            """, nativeQuery = true)
    int insertPendingIgnoreDuplicate(
            @Param("consumerName") String consumerName,
            @Param("eventId") String eventId,
            @Param("eventType") String eventType,
            @Param("payload") String payload,
            @Param("status") String status
    );

    Optional<InboxMessage> findByConsumerNameAndEventId(String consumerName, String eventId);

    @Query("""
            select message
            from InboxMessage message
            where message.consumerName = :consumerName
              and message.status = :status
              and (message.nextRetryAt is null or message.nextRetryAt <= :baseTime)
            order by message.createdAt asc
            """)
    List<InboxMessage> findDueMessages(
            @Param("consumerName") String consumerName,
            @Param("status") InboxStatus status,
            @Param("baseTime") LocalDateTime baseTime,
            Pageable pageable
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update InboxMessage message
               set message.status = :nextStatus,
                   message.leaseUntil = :leaseUntil,
                   message.lastError = null
             where message.id = :id
               and message.status = :expectedStatus
               and (message.nextRetryAt is null or message.nextRetryAt <= :baseTime)
            """)
    int claimDueMessage(
            @Param("id") Long id,
            @Param("expectedStatus") InboxStatus expectedStatus,
            @Param("nextStatus") InboxStatus nextStatus,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("baseTime") LocalDateTime baseTime
    );

    @Query("""
            select message
            from InboxMessage message
            where message.consumerName = :consumerName
              and message.status = :status
              and message.leaseUntil is not null
              and message.leaseUntil <= :baseTime
            order by message.leaseUntil asc
            """)
    List<InboxMessage> findStaleProcessingMessages(
            @Param("consumerName") String consumerName,
            @Param("status") InboxStatus status,
            @Param("baseTime") LocalDateTime baseTime,
            Pageable pageable
    );
}
