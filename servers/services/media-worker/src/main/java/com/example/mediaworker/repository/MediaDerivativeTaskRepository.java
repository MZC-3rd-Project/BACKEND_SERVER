package com.example.mediaworker.repository;

import com.example.mediaworker.entity.MediaDerivativeProfile;
import com.example.mediaworker.entity.MediaDerivativeTask;
import com.example.mediaworker.entity.MediaDerivativeTaskStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MediaDerivativeTaskRepository extends JpaRepository<MediaDerivativeTask, Long> {

    long countByStatus(MediaDerivativeTaskStatus status);

    Optional<MediaDerivativeTask> findByMediaIdAndDerivativeProfileAndMediaVersion(
            Long mediaId,
            MediaDerivativeProfile derivativeProfile,
            Long mediaVersion
    );

    @Query("""
            select task
            from MediaDerivativeTask task
            where task.status = :status
              and (task.nextRetryAt is null or task.nextRetryAt <= :baseTime)
            order by task.createdAt asc
            """)
    List<MediaDerivativeTask> findDueTasks(
            @Param("status") MediaDerivativeTaskStatus status,
            @Param("baseTime") LocalDateTime baseTime,
            Pageable pageable
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update MediaDerivativeTask task
               set task.status = :nextStatus,
                   task.processingStartedAt = :processingStartedAt,
                   task.lastError = null
             where task.id = :taskId
               and task.status = :expectedStatus
               and (task.nextRetryAt is null or task.nextRetryAt <= :baseTime)
            """)
    int claimDueTask(
            @Param("taskId") Long taskId,
            @Param("expectedStatus") MediaDerivativeTaskStatus expectedStatus,
            @Param("nextStatus") MediaDerivativeTaskStatus nextStatus,
            @Param("baseTime") LocalDateTime baseTime,
            @Param("processingStartedAt") LocalDateTime processingStartedAt
    );

    @Query("""
            select task
            from MediaDerivativeTask task
            where task.status = :status
              and task.processingStartedAt is not null
              and task.processingStartedAt <= :staleBefore
            order by task.processingStartedAt asc
            """)
    List<MediaDerivativeTask> findStaleProcessingTasks(
            @Param("status") MediaDerivativeTaskStatus status,
            @Param("staleBefore") LocalDateTime staleBefore,
            Pageable pageable
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update MediaDerivativeTask task
               set task.status = :nextStatus,
                   task.processingStartedAt = null,
                   task.nextRetryAt = :nextRetryAt,
                   task.lastError = :lastError
             where task.id = :taskId
               and task.status = :expectedStatus
               and task.processingStartedAt is not null
               and task.processingStartedAt <= :staleBefore
            """)
    int recoverStaleTask(
            @Param("taskId") Long taskId,
            @Param("expectedStatus") MediaDerivativeTaskStatus expectedStatus,
            @Param("nextStatus") MediaDerivativeTaskStatus nextStatus,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("lastError") String lastError
    );
}
