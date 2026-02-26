package com.example.search.repository;

import com.example.search.entity.SearchThumbnailEnrichmentStatus;
import com.example.search.entity.SearchThumbnailEnrichmentTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SearchThumbnailEnrichmentTaskRepository extends JpaRepository<SearchThumbnailEnrichmentTask, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM SearchThumbnailEnrichmentTask t WHERE t.itemId = :itemId")
    Optional<SearchThumbnailEnrichmentTask> findByItemIdForUpdate(@Param("itemId") Long itemId);

    List<SearchThumbnailEnrichmentTask> findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            SearchThumbnailEnrichmentStatus status,
            LocalDateTime now
    );

    List<SearchThumbnailEnrichmentTask> findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
            SearchThumbnailEnrichmentStatus status,
            LocalDateTime updatedAt
    );

    @Modifying
    @Query("UPDATE SearchThumbnailEnrichmentTask t " +
            "SET t.status = :processing, t.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE t.id = :id AND t.status = :pending AND t.nextRetryAt <= :now")
    int claimForProcessing(@Param("id") Long id,
                           @Param("pending") SearchThumbnailEnrichmentStatus pending,
                           @Param("processing") SearchThumbnailEnrichmentStatus processing,
                           @Param("now") LocalDateTime now);
}
