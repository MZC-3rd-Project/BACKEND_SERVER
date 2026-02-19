package com.example.notification.repository;

import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
            SELECT n
            FROM Notification n
            WHERE n.recipientId = :recipientId
              AND (:cursorId IS NULL OR n.id < :cursorId)
            ORDER BY n.id DESC
            """)
    List<Notification> findByRecipientIdWithCursor(@Param("recipientId") Long recipientId,
                                                    @Param("cursorId") Long cursorId,
                                                    org.springframework.data.domain.Pageable pageable);

    long countByRecipientIdAndIsReadFalse(Long recipientId);

    Optional<Notification> findByDedupeKey(String dedupeKey);

    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Notification n
               SET n.isRead = true,
                   n.readAt = :readAt,
                   n.status = :status
             WHERE n.recipientId = :recipientId
               AND n.isRead = false
               AND n.deletedAt IS NULL
            """)
    int markAllAsRead(@Param("recipientId") Long recipientId,
                      @Param("readAt") LocalDateTime readAt,
                      @Param("status") NotificationStatus status);
}
