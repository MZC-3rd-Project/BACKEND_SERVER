package com.example.product.repository;

import com.example.product.entity.image.ItemMediaLinkSyncStatus;
import com.example.product.entity.image.ItemMediaLinkSyncTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ItemMediaLinkSyncTaskRepository extends JpaRepository<ItemMediaLinkSyncTask, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM ItemMediaLinkSyncTask t WHERE t.itemId = :itemId")
    Optional<ItemMediaLinkSyncTask> findByItemIdForUpdate(@Param("itemId") Long itemId);

    List<ItemMediaLinkSyncTask> findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            ItemMediaLinkSyncStatus status, LocalDateTime now);

    List<ItemMediaLinkSyncTask> findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
            ItemMediaLinkSyncStatus status, LocalDateTime updatedAt);

    @Modifying
    @Query("UPDATE ItemMediaLinkSyncTask t SET t.status = :processing, t.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE t.id = :id AND t.status = :pending AND t.nextRetryAt <= :now")
    int claimForProcessing(@Param("id") Long id,
                           @Param("pending") ItemMediaLinkSyncStatus pending,
                           @Param("processing") ItemMediaLinkSyncStatus processing,
                           @Param("now") LocalDateTime now);
}
