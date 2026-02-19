package com.example.notification.repository;

import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationDelivery;
import com.example.notification.entity.NotificationDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    Optional<NotificationDelivery> findByNotificationIdAndChannel(Long notificationId, NotificationChannel channel);

    List<NotificationDelivery> findByNotificationIdOrderByIdAsc(Long notificationId);

    List<NotificationDelivery> findByNotificationIdInOrderByNotificationIdAscIdAsc(List<Long> notificationIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE NotificationDelivery d
               SET d.nextRetryAt = :leaseUntil
             WHERE d.id = :deliveryId
               AND d.status IN :claimableStatuses
               AND (d.nextRetryAt IS NULL OR d.nextRetryAt <= :now)
            """)
    int claimDispatchLock(@Param("deliveryId") Long deliveryId,
                          @Param("claimableStatuses") Set<NotificationDeliveryStatus> claimableStatuses,
                          @Param("now") LocalDateTime now,
                          @Param("leaseUntil") LocalDateTime leaseUntil);

    @Query("""
            SELECT d
            FROM NotificationDelivery d
            WHERE d.status IN :statuses
              AND (d.nextRetryAt IS NULL OR d.nextRetryAt <= :now)
            ORDER BY d.id ASC
            """)
    List<NotificationDelivery> findDispatchTargets(@Param("statuses") Set<NotificationDeliveryStatus> statuses,
                                                   @Param("now") LocalDateTime now,
                                                   org.springframework.data.domain.Pageable pageable);
}
