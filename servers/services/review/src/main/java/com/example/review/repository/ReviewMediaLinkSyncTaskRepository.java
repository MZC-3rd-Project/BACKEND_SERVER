package com.example.review.repository;

import com.example.review.entity.media.ReviewMediaLinkSyncStatus;
import com.example.review.entity.media.ReviewMediaLinkSyncTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReviewMediaLinkSyncTaskRepository extends JpaRepository<ReviewMediaLinkSyncTask, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM ReviewMediaLinkSyncTask t WHERE t.reviewId = :reviewId")
    Optional<ReviewMediaLinkSyncTask> findByReviewIdForUpdate(@Param("reviewId") Long reviewId);

    List<ReviewMediaLinkSyncTask> findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            ReviewMediaLinkSyncStatus status, LocalDateTime now);

    List<ReviewMediaLinkSyncTask> findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
            ReviewMediaLinkSyncStatus status, LocalDateTime updatedAt);

    @Modifying
    @Query("UPDATE ReviewMediaLinkSyncTask t SET t.status = :processing, t.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE t.id = :id AND t.status = :pending AND t.nextRetryAt <= :now")
    int claimForProcessing(@Param("id") Long id,
                           @Param("pending") ReviewMediaLinkSyncStatus pending,
                           @Param("processing") ReviewMediaLinkSyncStatus processing,
                           @Param("now") LocalDateTime now);
}
