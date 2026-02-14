package com.example.funding.repository;

import com.example.funding.entity.StockCancelRetry;
import com.example.funding.entity.StockCancelRetryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StockCancelRetryRepository extends JpaRepository<StockCancelRetry, Long> {

    List<StockCancelRetry> findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            StockCancelRetryStatus status, LocalDateTime now);

    @Modifying
    @Query("UPDATE StockCancelRetry r SET r.status = :processing " +
            "WHERE r.id = :id AND r.status = :pending AND r.nextRetryAt <= :now")
    int claimForProcessing(@Param("id") Long id,
                           @Param("pending") StockCancelRetryStatus pending,
                           @Param("processing") StockCancelRetryStatus processing,
                           @Param("now") LocalDateTime now);
}
