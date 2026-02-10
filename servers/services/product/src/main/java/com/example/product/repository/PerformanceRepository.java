package com.example.product.repository;

import com.example.product.entity.performance.Performance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    Optional<Performance> findByItemId(Long itemId);

    List<Performance> findByItemIdIn(List<Long> itemIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Performance p SET p.deletedAt = CURRENT_TIMESTAMP WHERE p.itemId = :itemId AND p.deletedAt IS NULL")
    void softDeleteByItemId(@Param("itemId") Long itemId);
}
