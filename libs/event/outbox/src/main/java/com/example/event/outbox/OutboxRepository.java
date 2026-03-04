package com.example.event.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

    @Query("SELECT o FROM OutboxMessage o WHERE o.status = :status AND o.createdAt < :before ORDER BY o.createdAt ASC")
    List<OutboxMessage> findByStatusAndCreatedBefore(
            @Param("status") OutboxStatus status,
            @Param("before") LocalDateTime before);

    @Query(value = "SELECT * FROM outbox_messages WHERE status = :status AND created_at < :before " +
            "AND (retry_count = 0 OR EXTRACT(EPOCH FROM (:now - updated_at)) >= LEAST(" +
            "CAST(:maxRetryDelaySeconds AS DOUBLE PRECISION), " +
            "CAST(:baseRetryDelaySeconds AS DOUBLE PRECISION) * POWER(2, GREATEST(retry_count - 1, 0))" +
            ")) " +
            "ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxMessage> findPendingMessagesForRelay(
            @Param("status") String status,
            @Param("before") LocalDateTime before,
            @Param("now") LocalDateTime now,
            @Param("baseRetryDelaySeconds") long baseRetryDelaySeconds,
            @Param("maxRetryDelaySeconds") long maxRetryDelaySeconds,
            @Param("limit") int limit);

    @Modifying
    @Transactional
    @Query("UPDATE OutboxMessage o SET o.status = :newStatus, o.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE o.id = :id AND o.status = :currentStatus")
    int updateStatusById(@Param("id") Long id,
                         @Param("currentStatus") OutboxStatus currentStatus,
                         @Param("newStatus") OutboxStatus newStatus);

    @Modifying
    @Transactional
    @Query("UPDATE OutboxMessage o SET o.status = :newStatus, o.publishedAt = :publishedAt, o.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE o.id = :id AND o.status = :currentStatus")
    int markAsPublishedById(@Param("id") Long id,
                            @Param("currentStatus") OutboxStatus currentStatus,
                            @Param("newStatus") OutboxStatus newStatus,
                            @Param("publishedAt") LocalDateTime publishedAt);

    @Modifying
    @Transactional
    @Query("UPDATE OutboxMessage o SET o.updatedAt = :claimedAt " +
            "WHERE o.id = :id AND o.status = :status AND o.updatedAt <= :staleBefore")
    int claimStaleSendingMessage(@Param("id") Long id,
                                 @Param("status") OutboxStatus status,
                                 @Param("staleBefore") LocalDateTime staleBefore,
                                 @Param("claimedAt") LocalDateTime claimedAt);

    List<OutboxMessage> findByStatusOrderByCreatedAtAsc(OutboxStatus status);

    List<OutboxMessage> findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
            OutboxStatus status, LocalDateTime updatedAt);

    @Modifying
    @Transactional
    @Query("DELETE FROM OutboxMessage o WHERE o.status = :status AND o.createdAt < :before")
    int deleteByStatusAndCreatedBefore(
            @Param("status") OutboxStatus status,
            @Param("before") LocalDateTime before);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM outbox_messages WHERE id IN (" +
            "SELECT id FROM outbox_messages WHERE status = :status AND created_at < :before " +
            "ORDER BY created_at ASC LIMIT :limit" +
            ")",
            nativeQuery = true)
    int deleteTopByStatusAndCreatedBefore(
            @Param("status") String status,
            @Param("before") LocalDateTime before,
            @Param("limit") int limit);
}
